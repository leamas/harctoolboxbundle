/*
Copyright (C) 2012 Bengt Martensson.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or (at
your option) any later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License along with
this program. If not, see http://www.gnu.org/licenses/.
*/

package org.harctoolbox.harchardware;

import org.harctoolbox.harchardware.ir.GlobalCache;
import org.harctoolbox.harchardware.comm.TcpSocketPort;
import org.harctoolbox.harchardware.comm.UrlPort;
import org.harctoolbox.harchardware.comm.UdpSocketPort;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import java.io.IOException;
import java.util.ArrayList;
import org.harctoolbox.IrpMaster.IrpUtils;
import org.harctoolbox.harchardware.comm.LocalSerialPortBuffered;
import org.harctoolbox.harchardware.ir.NoSuchTransmitterException;

/**
 * Gives possibilities to invoke many of the functions from the command line. Demonstrates the interfaces.
 */

public class MainString {

    //private final static int invalidPort = -1;
    private final static int defaultPortNumber = 1;

    private MainString() {
    }

    private static String join(ArrayList<String> arr, String separator) {
        StringBuilder result = new StringBuilder();
        for (String s : arr)
            result.append(result.length() == 0 ? "" : separator).append(s);
        return result.toString();
    }


    private static int noTrue(boolean... bool) {
        int sum = 0;
        for (boolean b : bool)
            if (b)
                sum++;

        return sum;
    }

    private static void usage(int exitcode) {
        StringBuilder str = new StringBuilder();
        argumentParser.usage(str);

        str.append("\n"
                + "parameters: <protocol> <deviceno> [<subdevice_no>] commandno [<toggle>]\n"
                + "   or       <Pronto code>");

        (exitcode == IrpUtils.exitSuccess ? System.out : System.err).println(str);
        doExit(exitcode);
    }

    private static void doExit(int exitcode) {
        System.exit(exitcode);
    }

    private final static class CommandLineArgs {

        @Parameter(names = {"-1"}, description = "Expect one line of response")
        private boolean oneLine;

        @Parameter(names = {"-2"}, description = "Expect two line of response")
        private boolean twoLines;

        @Parameter(names = {"-#", "--count"}, description = "Number of times to send sequence")
        private int count = 1;

        //@Parameter(names = {"-d", "--debug"}, description = "Debug code")
        //private int debug = 0;

        @Parameter(names = {"--delay"}, description = "Delay between commands in milliseconds")
        private int delay = 0;

        @Parameter(names = {"-g", "--globalcache"}, description = "Use GlobalCache")
        private boolean globalcache = false;

        @Parameter(names = {"-h", "--help", "-?"}, description = "Display help message")
        private boolean helpRequested = false;

        @Parameter(names = {"--http", "--url"}, description = "Use URLs (http)")
        private boolean url = false;

        @Parameter(names = {"-i", "--ip"}, description = "IP address or name")
        private String ip = null;

        //@Parameter(names = {"-I", "--irtrans"}, description = "Use IrTrans")
        //private boolean irtrans = false;

        @Parameter(names = {"-m", "--myip"}, description = "For UPD only: IP number to listen to")
        private String myIp = null;

        @Parameter(names = {"-p", "--port"}, description = "Port number, either TCP port number, or serial port number (counting the first as 1).")
        private int portNumber = defaultPortNumber;

        @Parameter(names = {"--prefix"}, description = "Prefix to be prepended to all sent commands.")
        private String prefix = null;

        @Parameter(names = {"-r", "--return"}, description = "Append a carrage return at the end of the command.")
        private boolean appendReturn;

        @Parameter(names = {"-s", "--serial"}, description = "Use local serial port.")
        private boolean serial;

        @Parameter(names = {"--suffix"}, description = "Sufffix to be appended to all sent commands.")
        private String suffix = null;

        @Parameter(names = {"-t", "--tcp"}, description = "Use tcp sockets")
        private boolean tcp;

        @Parameter(names = {"-T", "--timeout"}, description = "Timeout in milliseconds")
        private int timeout = 2000;

        @Parameter(names = {"--telnet"}, description = "Go in interactive telnet mode")
        private boolean telnet;

        @Parameter(names = {"-u", "--upper"}, description = "Translate commands to upper case.")
        private boolean toUpper;

        @Parameter(names = {"--udp"}, description = "Use Udp sockets.")
        private boolean udp;

        @Parameter(names = {"-v", "--version"}, description = "Display version information")
        private boolean versionRequested;

        @Parameter(names = {"-V", "--verbose"}, description = "Turn on verbose reporting")
        private boolean verbose;

        @Parameter(description = "[parameters]")
        private ArrayList<String> parameters = new ArrayList<String>();
    }

    private static JCommander argumentParser;
    private static CommandLineArgs commandLineArgs = new CommandLineArgs();

    public static void main(String[] args) {
        argumentParser = new JCommander(commandLineArgs);
        argumentParser.setProgramName("HarcHardware");

        try {
            argumentParser.parse(args);
        } catch (ParameterException ex) {
            System.err.println(ex.getMessage());
            usage(IrpUtils.exitUsageError);
        }

        if (commandLineArgs.helpRequested)
            usage(IrpUtils.exitSuccess);

        if (commandLineArgs.versionRequested) {
            System.out.println(Version.versionString);
            System.out.println("JVM: " + System.getProperty("java.vendor") + " " + System.getProperty("java.version") + " " + System.getProperty("os.name") + "-" + System.getProperty("os.arch"));
            System.out.println();
            System.out.println(Version.licenseString);
            System.exit(IrpUtils.exitSuccess);
        }

        if (noTrue(commandLineArgs.url, commandLineArgs.serial, commandLineArgs.globalcache, commandLineArgs.tcp, commandLineArgs.udp) != 1) {
            System.err.println("Exactly one of --serial, --globalcache, --udp, --url, and --tcp must be given.");
            System.exit(IrpUtils.exitUsageError);
        }

        boolean didSomethingUseful = false;
        GlobalCache globalCache = null;
        LocalSerialPortBuffered localSerialPortBuffered = null;
        TcpSocketPort tcpPort = null;
        UdpSocketPort udpPort = null;
        UrlPort urlPort = null;
        IStringCommand harcHardware = null;
        String localIpAddress = commandLineArgs.myIp; // TODO: presently not used

        try {
            if (commandLineArgs.globalcache) {
                globalCache = new GlobalCache(commandLineArgs.ip, commandLineArgs.verbose, commandLineArgs.timeout, false);
                harcHardware = globalCache.getSerialPort(commandLineArgs.portNumber);
            } else if (commandLineArgs.url) {
                if (commandLineArgs.ip == null) {
                    System.err.println("Must give a sensible hostname for URL.");
                    System.exit(IrpUtils.exitUsageError);
                }

                // Can use either the framer or prefix/suffix in the UrlPort class.
                // I select to use the framer.
                urlPort = new UrlPort("http", commandLineArgs.ip, commandLineArgs.portNumber,
                        null /*commandLineArgs.prefix*/, null /*commandLineArgs.suffix*/,
                        commandLineArgs.timeout, commandLineArgs.verbose);
                harcHardware = urlPort;
            } else if (commandLineArgs.tcp) {
                if (commandLineArgs.portNumber == defaultPortNumber) {
                    System.err.println("Must give a sensible port number for TCP.");
                    System.exit(IrpUtils.exitUsageError);
                }
                if (commandLineArgs.ip == null) {
                    System.err.println("Must give a sensible hostname for TCP.");
                    System.exit(IrpUtils.exitUsageError);
                }
                tcpPort = new TcpSocketPort(commandLineArgs.ip, commandLineArgs.portNumber, commandLineArgs.timeout, commandLineArgs.verbose, TcpSocketPort.ConnectionMode.keepAlive);
                harcHardware = tcpPort;
            } else if (commandLineArgs.udp) {
                if (commandLineArgs.portNumber == defaultPortNumber) {
                    System.err.println("Must give a sensible port number for UDP.");
                    System.exit(IrpUtils.exitUsageError);
                }
                if (commandLineArgs.ip == null) {
                    System.err.println("Must give a sensible hostname for UDP.");
                    System.exit(IrpUtils.exitUsageError);
                }
                if (commandLineArgs.myIp == null) {
                    System.err.print("No own IP address given, let's try the environment...");
                    localIpAddress = Utils.getHostname();
                    System.err.println("using \"" + localIpAddress + "\"");
                }

                udpPort = new UdpSocketPort(commandLineArgs.ip, commandLineArgs.portNumber, commandLineArgs.timeout, commandLineArgs.verbose);
                harcHardware = udpPort;
            } else if (commandLineArgs.serial) {
                localSerialPortBuffered = new LocalSerialPortBuffered(commandLineArgs.portNumber);
                harcHardware = localSerialPortBuffered;
            }

            StringCommander.Framer framer = new StringCommander.Framer(
                    commandLineArgs.prefix + "{0}" + commandLineArgs.suffix + (commandLineArgs.appendReturn ? "\r" : ""),
                    commandLineArgs.toUpper);

            if (commandLineArgs.parameters.isEmpty() && didSomethingUseful)
                    System.exit(IrpUtils.exitSuccess);

            if (commandLineArgs.parameters.isEmpty() || commandLineArgs.telnet) {
                if (!commandLineArgs.telnet)
                    System.err.println("No arguments given, going into interactive telnet mode.");

                System.err.println("Type quit to quit.");
                StringCommander.telnet(harcHardware, framer);
            } else {
                String command = framer.frame(join(commandLineArgs.parameters, " "));
                int returnLines = commandLineArgs.oneLine ? 1
                        : commandLineArgs.twoLines ? 2
                        : 0;
                StringCommander stringCommander = new StringCommander(harcHardware);
                String[] result = stringCommander.sendStringCommand(
                        new String[]{ command }, commandLineArgs.count, returnLines, commandLineArgs.delay);
                for (String s : result)
                    System.out.println(s);
                if (harcHardware != null)
                    harcHardware.close();
            }
        } catch (NoSuchTransmitterException ex) {
            System.err.println(ex.getMessage());
            System.exit(IrpUtils.exitFatalProgramFailure);
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            System.exit(IrpUtils.exitFatalProgramFailure);
        } catch (NoSuchPortException ex) {
            System.err.println("No such port: " + ex.getMessage());
            System.exit(IrpUtils.exitFatalProgramFailure);
        } catch (PortInUseException ex) {
            System.err.println(ex.getMessage());
            System.exit(IrpUtils.exitFatalProgramFailure);
        } catch (UnsupportedCommOperationException ex) {
            System.err.println(ex.getMessage());
            System.exit(IrpUtils.exitFatalProgramFailure);
        }
    }
}
