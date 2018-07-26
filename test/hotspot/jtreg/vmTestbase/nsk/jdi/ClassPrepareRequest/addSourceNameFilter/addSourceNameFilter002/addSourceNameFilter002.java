/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @modules java.base/jdk.internal.misc:+open
 *
 * @summary converted from VM Testbase nsk/jdi/ClassPrepareRequest/addSourceNameFilter/addSourceNameFilter002.
 * VM Testbase keywords: [quick, jpda, jdi, feature_jdk6_jpda, vm6]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test checks up that a result of the method com.sun.jdi.ClassPrepareRequest.addSourceNameFilter(String sourceNamePattern)
 *     complies with its spec:
 *     "Restricts the events generated by this request to the preparation of reference types for which the
 *     restricted regular expression 'sourceNamePattern' matches one of the 'sourceNames' for the reference type
 *     being prepared."
 *     Test scenario:
 *     Debugger create following class file:
 *         - class file containing multiple strata
 *         - SourceDebugExtension's source map refers to multiple sources
 *     Debugger VM create ClassPrepareEventRequest, add source name filter(use names of all sources which was added to class
 *     file) and force debuggee load class from generated class file. When debuggee finish class loading debugger check is
 *     ClassPrepareEvent was correct filtered or not.
 *     Following source name filters are used:
 *         - exact source name
 *         - invalid source name (don't expect events with this filter)
 *         - pattern starting with '*'
 *         - pattern ending with '*'
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver jdk.test.lib.FileInstaller . .
 * @build nsk.jdi.ClassPrepareRequest.addSourceNameFilter.addSourceNameFilter002.addSourceNameFilter002
 *        nsk.share.jdi.TestClass1
 * @run main/othervm PropertyResolvingWrapper
 *      nsk.jdi.ClassPrepareRequest.addSourceNameFilter.addSourceNameFilter002.addSourceNameFilter002
 *      -verbose
 *      -arch=${os.family}-${os.simpleArch}
 *      -waittime=5
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      "-debugee.vmkeys=${test.vm.opts} ${test.java.opts}"
 *      -testClassPath ${test.class.path}
 *      -testWorkDir .
 *      -sourceCount 2
 */

package nsk.jdi.ClassPrepareRequest.addSourceNameFilter.addSourceNameFilter002;

import java.io.*;
import java.util.ArrayList;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import nsk.share.Consts;
import nsk.share.TestBug;
import nsk.share.jdi.*;
import nsk.share.jdi.sde.*;
import nsk.share.jpda.AbstractDebuggeeTest;

public class addSourceNameFilter002 extends SDEDebugger {

    private static final String DEBUGGEE_MAIN_THREAD = "main";

    public static void main(String argv[]) {
        System.exit(run(argv, System.out) + Consts.JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream out) {
        return new addSourceNameFilter002().runIt(argv, out);
    }

    private static volatile int eventReceived;

    protected boolean canRunTest() {
        if (!vm.canUseSourceNameFilters()) {
            log.display("TEST CANCELLED due to:  vm.canUseSourceNameFilters() = false");
            return false;
        } else
            return true;
    }

    private int sourceCount;

    protected String[] doInit(String args[], PrintStream out) {
        args = super.doInit(args, out);

        ArrayList<String> standardArgs = new ArrayList<String>();

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-sourceCount") && (i < args.length - 1)) {
                sourceCount = Integer.parseInt(args[i + 1]);
                i++;
            } else
                standardArgs.add(args[i]);
        }

        if (sourceCount == 0) {
            throw new TestBug("Debugger requires 'sourceCount' parameter");
        }

        return standardArgs.toArray(new String[] {});
    }

    protected String debuggeeClassName() {
        // debugee should load classes from test work directory
        return AbstractJDIDebuggee.class.getName() + " -testClassPath " + testWorkDir;
    }

    // listener counting ClassPrepareEvent
    public class ClassPrepareEventListener extends EventHandler.EventListener {

        public boolean eventReceived(Event event) {
            if (event instanceof ClassPrepareEvent) {
                ClassPrepareEvent classPrepareEvent = (ClassPrepareEvent) event;
                ThreadReference thread = classPrepareEvent.thread();
                if (thread != null && DEBUGGEE_MAIN_THREAD.equals(thread.name())) {
                    eventReceived++;
                }

                log.display("Event received: " + event + " Class: " + classPrepareEvent.referenceType().name() +
                        " Thread:" + (thread != null ? thread.name() : ""));

                vm.resume();

                return true;
            }

            return false;
        }
    }

    private EventHandler eventHandler;

    private void testSourceFilter(String className, String sourceName, boolean expectEvent) {
        ClassPrepareRequest request;

        // create request with filter
        request = debuggee.getEventRequestManager().createClassPrepareRequest();
        request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        request.addSourceNameFilter(sourceName);
        request.enable();

        // Reset event counter
        eventReceived = 0;
        // force debuggee load class
        pipe.println(AbstractDebuggeeTest.COMMAND_LOAD_CLASS + ":" + className);

        if (!isDebuggeeReady())
            return;

        request.disable();

        // check is event was correct filtered or not
        if (expectEvent) {
            if (eventReceived == 0) {
                setSuccess(false);
                log.complain("Expected ClassPrepareEvent was not received");
            }
        } else {
            if (eventReceived > 0) {
                setSuccess(false);
                log.complain("Unexpected ClassPrepareEvent was received");
            }
        }
    }

    // create class file with multiple strata
    private void preparePathcedClassFile(String className, String testStratumSourceNames[]) {
        String smapFileName = "TestSMAP.smap";

        SmapGenerator smapGenerator = new SmapGenerator();

        SmapStratum smapStratum = new SmapStratum("TestStratum");

        for (String testStratumSourceName : testStratumSourceNames) {
            smapStratum.addFile(testStratumSourceName);
            // add dummy line data
            smapStratum.addLineData(1, testStratumSourceName, 1, 1, 1);
            smapGenerator.addStratum(smapStratum, true);
        }

        savePathcedClassFile(className, smapGenerator, smapFileName);
    }

    public void doTest() {
        String className = "nsk.share.jdi.TestClass1";
        String sourceName = "TestClass1.java";

        String testStratumSourceNames[] = new String[sourceCount];
        // {"TestStratum1_Source.tss1", "TestStratum2_Source.tss2"};

        for (int i = 0; i < testStratumSourceNames.length; i++)
            testStratumSourceNames[i] = "TestStratum" + i + "_Source.tss" + i;

        // create class file with multiple strata
        preparePathcedClassFile(className, testStratumSourceNames);

        eventHandler = new EventHandler(debuggee, log);
        eventHandler.startListening();

        // Add a listener to count ClassPrepare events.
        // The listener should be added after EventHandler.startListening()
        // is called to ensure it will be the first to process events.
        eventHandler.addListener(new ClassPrepareEventListener());

        // set valid source name
        testSourceFilter(className, sourceName, true);
        for (int i = 0; i < testStratumSourceNames.length; i++) {
            testSourceFilter(className, testStratumSourceNames[i], true);
        }

        // set invalid source name, don't expect events
        testSourceFilter(className, sourceName + "_InvalidSourceName", false);
        for (int i = 0; i < testStratumSourceNames.length; i++) {
            testSourceFilter(className, testStratumSourceNames[i] + "_InvalidSourceName", false);
        }

        // use pattern filter
        testSourceFilter(className, "TestClass1*", true);

        for (int i = 0; i < testStratumSourceNames.length; i++)
            testSourceFilter(className, "TestStratum" + i + "*", true);

        testSourceFilter(className, "*.java", true);

        for (int i = 0; i < testStratumSourceNames.length; i++)
            testSourceFilter(className, "*.tss" + i, true);

        eventHandler.stopEventHandler();
    }
}
