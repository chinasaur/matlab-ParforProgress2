/*
 * Copyright (c) 2010-2012, Andreas Kotowicz
 *
 *
 * ideas for this code are from:
 * http://download.oracle.com/javase/tutorial/networking/sockets/clientServer.html
 * http://www.mathworks.com/matlabcentral/fileexchange/24594-parfor-progress-monitor
 *
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */ 

import java.io.*;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import javax.swing.*;
import javax.swing.Timer;

import java.awt.GridLayout;
import java.awt.event.*;

import java.util.concurrent.atomic.AtomicBoolean;

public class ParforProgressServer2 implements Runnable, ActionListener {

	private JFrame fFrame;
	private JLabel fLabel;
	private JProgressBar fBar;
    
	private String taskname;
	private String ETA_string_default = "ETA: unknown";
	private String ETA_string = ETA_string_default;
	private String runtime_prev = "Os";

	private int counter;
    private int DEBUG = 0;
    
    private boolean listening = true;

    private Thread fThread;
    private AtomicBoolean fKeepGoing;
    
    private Timer gui_timer; // the timer updating the run time
	
	public synchronized int CountUp(){
		return counter++;
	}
    
    private boolean started_from_console = false;
    
	public synchronized boolean set_started_from_console(){
        started_from_console = true;
		return started_from_console;
	}    
    
	public synchronized boolean get_started_from_console(){
		return started_from_console;
	}      
    
    public synchronized void updateGUI(){
        
        // update percentage text
        fBar.setValue(counter);
        if (DEBUG == 1)
            fFrame.setTitle("counter: " +  counter);
        
        // calculate fraction & update timebar
        int reminder = (int)( counter % fraction_all );
        if (reminder == 0) {
            ETA_string = "ETA: " + ETA(fBar.getMaximum());
        }
        
        // We are done with all loops
        if (counter == fBar.getMaximum()) {
            done_from_GUI();
        }
    }

    private double goal;
    private double PERCENTAGE = 0.05;
    private double fraction_all;
    // time between running time updates
    private int update_timer_each_ms = 1000;
    private double update_timer_each_s = update_timer_each_ms / 1000;
    private double ETA_time = 0;
    
    // when did the monitor start / stop?
    private static double time_start;
    private static double time_stop; 
    
	private ServerSocketChannel serverSocket;
    private SocketChannel sc;
    
    // see http://users.dickinson.edu/~braught/courses/cs132s01/classes/code/Rounding.src.html
    public static double Round(double val, int places) {
        long factor = (long) Math.pow(10, places);
        
        // Shift the decimal the correct number of places
        // to the right.
        val = val * factor;
        
        // Round to the nearest integer.
        long tmp = Math.round(val);
        
        // Shift the decimal the correct number of places
        // back to the left.
        return (double) tmp / factor;
    }    
    
    public static String CurrentRuntime(int precision) {
        String runtime;
        time_stop = System.currentTimeMillis();
        
        runtime = Double.toString(Round((time_stop-time_start)/1000, precision));
        
        // remove trailing '.0'
        if (precision == 0) {
            runtime = runtime.substring(0, runtime.length()-2);
        }
        
        runtime = runtime + "s";
        return runtime;
    }    
    
    public String ETA(int maximum) {
        String eta;
        double percentage_done, time_per_percent, time_passed, time_to_go;
        
        time_stop = System.currentTimeMillis();
        time_passed = Round((time_stop-time_start)/1000, 2);
        
        percentage_done = (double) counter / (double) maximum;
        
        time_per_percent = time_passed / percentage_done;
        time_to_go = (1 - percentage_done) * time_per_percent;
        
        ETA_time = Round(time_to_go, 2);
        eta = ETA_time + "s";
        return eta;
    }      
    
    /**
     * Create a "server" progress monitor - this runs on the desktop client and
     * pops up the progress monitor UI.
     */
    public static ParforProgressServer2 createServer(String s, int N, double update_percentage) throws IOException {
        ParforProgressServer2 ret = new ParforProgressServer2();
        ret.setup(s, N, update_percentage, 0);
        ret.start();
        return ret;
    }    
    
	private void StartGUI(String s, int N, double update_percentage ) {
		
        /* override user specified "update_percentage" if it doesn't make
         * any sense
         */
        if (N >= 1000 && update_percentage > 0.01) {
            update_percentage = 0.01;
        } else if (N >= 100 && update_percentage > 0.1) {
            update_percentage = 0.1;
        }
        
        taskname = s;
        
		// build the UI
		fFrame = new JFrame(taskname);
		fLabel = new JLabel(taskname);
		fBar   = new JProgressBar(0, N);
		fBar.setStringPainted(true);
		fFrame.setLayout(new GridLayout(2, 1));
		fFrame.getContentPane().add(fLabel);
		fFrame.getContentPane().add(fBar);
		fFrame.pack();
		fFrame.setLocationRelativeTo(null);
		
		fFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		fFrame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				if (JOptionPane.showConfirmDialog(new JFrame(),
						"Are you sure you want to close the progressbar window?\n"
						+ "Your computation will continue but you'll have no clue\n"
						+ "about its progress.", "You are about to close the progressbar window",
				        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
					fFrame.dispose();
				}
			}
		});
		
		fFrame.setSize(300, 75);
		fFrame.setVisible(true);
		fFrame.setResizable(true); 
        
        // how much do we have to do?
        goal = N;
        
        // initalize counter
        counter = 0;
        
        // update percentage
        PERCENTAGE = update_percentage;
        
        int precision = 2;
        double current = (double) (goal * PERCENTAGE);
        fraction_all = Round(current, precision);
        
        // everything is up and running, let's write down the time
        time_start = System.currentTimeMillis();
	
	}

    public static void main( String args[] ) {
        
        if (args.length < 2) {
            System.err.println("ParforProgressServer2: Please provide at least 2 input arguments (string, numberRuns).");
            System.exit(1);
        }
        
        String displayString = args[0];
        int totalNumberRuns = 10;
        double updateEachPercent = 0.1;
        
        if (args.length > 1) {
            try {
                totalNumberRuns = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("ParforProgressServer2: 2nd argument must be an integer");
                System.exit(1);
            }
        }

        if (args.length > 2) {
            try {
                updateEachPercent = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("ParforProgressServer2: 3rd argument must be a double");
                System.exit(1);
            }
        }
        
        ParforProgressServer2 myserver = new ParforProgressServer2();
        myserver.setup(displayString, totalNumberRuns, updateEachPercent, 1);
        myserver.start();
    }

    public int getPort() {
        return ((InetSocketAddress)serverSocket.socket().getLocalSocketAddress()).getPort();
    }
    
    public void setup(String s, int N, double update_percentage, int show_port) { 

        serverSocket = null;
        
        try {
            serverSocket = ServerSocketChannel.open();
            // increase the default backlog from 50 to 512 - this is 
            // hopefully enough for ultra short computation loops.
            serverSocket.socket().bind(null, 512);
            // performance (total run time) is up to 50% better (shorter) 
            // if I set this to 'true' instead of 'false' - I don't really 
            // understand why.
            serverSocket.configureBlocking(true);
            
            if (show_port == 1)
                set_started_from_console();
            
            if (DEBUG == 1 || show_port == 1)
                System.err.println("ParforProgressServer2: Server started on port " + getPort() );
            
            StartGUI(s, N, update_percentage);
            
        } catch (IOException e) {
            System.err.println("ParforProgressServer2: error while calling setup().");
            System.err.println(e);
            // this will close the JVM, i.e. also MATLAB!
            // System.exit(-1);
        }
        
        // Our background thread
        fThread = new Thread(this);
        fThread.setDaemon(true);
        
        // Used to indicate to fThread when it's time to go
        fKeepGoing = new AtomicBoolean(true);
        
        gui_timer = new Timer(update_timer_each_ms, this);
        gui_timer.start();
    }
    
    /**
     * Don't start the Thread in the constructor
     */
    public void start() { 
        fThread.start();
    }
    
    /**
     * action performed by "ActionListener" & "gui_timer"
     */
    public void actionPerformed(ActionEvent e) { 
        update_runtime();
    }
    
    
    /**
     * Loop over accepting connections and update
     */
    public void run() {
        
        while( fKeepGoing.get() ) {
            
            try {
                
                if (serverSocket != null) {
                    
                    while (listening){
                        
                        try {
                            // this line will be called only once, if
                            // 'matlabpool' is OFF.
                            sc = serverSocket.accept();
                            // this code will only be called if 'matlabpool' 
                            // is ON.
                            if (sc != null) {
                                this.increment();
                                sc.close();
                            }
                        } catch (IOException e) {
                            System.err.println(e);
                        }
                    }
                    
                    try {
                        sc.close();
                    } catch (IOException e) {
                        System.err.println("ParforProgressServer2: Problem closing socket in run().");
                        System.exit(-1);
                    }
                    
                }
                
            } catch( Exception e ) {
                if (fKeepGoing.get()) {
                    e.printStackTrace();
                }
            }
            
        }
    }
    
    /**
     * shut down functions
     */
    
    // called from matlab's delete()
    public void done() {
        show_execution_time();
        stop_program();
    }
    
    // called from within our updateGUI() function
    public void done_from_GUI() {
        // make sure we don't show the execution time twice. only show it 
        // here, if this program is running on the console (so no-one will 
        // call done() anymore.
        if (get_started_from_console() == true) {
            show_execution_time();
        }
        stop_program();
    }
    
    // the real code that stops everything
    public void stop_program() {
        listening = false;
        gui_timer.stop();
        fKeepGoing.set(false);
        fFrame.dispose();
    }
    
    public void show_execution_time() {
        System.out.flush();
        System.err.println("\n" + "  >> execution time was " + CurrentRuntime(2) + ".\n");
        System.err.flush();
    }

    
    public void update_runtime() {
        // tell user how much time has already passed.
        String new_time = CurrentRuntime(0);
        
        // update label only if new time string is different from previous one
        if (runtime_prev.equals(new_time) == false) {
            if ((ETA_string != ETA_string_default) && (ETA_time > update_timer_each_s)) {
                ETA_time = Round(ETA_time - update_timer_each_s, 2);
                ETA_string = "ETA: " + ETA_time + "s";
            }
            fFrame.setTitle("Runtime: " + new_time + " - " + ETA_string);            
            runtime_prev = new_time;
        }
    }
    
    
    /* 
     * this method will be used if matlabpool is OFF
     */
    public synchronized void increment() {
        if (DEBUG == 2)
            System.out.println("Calling server increment");

        CountUp();
        updateGUI();

    }
    
    
}

