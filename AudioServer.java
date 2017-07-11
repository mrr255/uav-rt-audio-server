import java.io.*;
import java.net.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.spi.AudioFileWriter;
import javax.sound.sampled.*;

import java.sql.Timestamp;
import java.util.Date;
import java.text.SimpleDateFormat;

public class AudioServer {

	private static String[] argsGB;
	private static DataOutputStream outGB;
	private static AudioInputStream micGB;
	private static FileOutputStream archiveGB;
	private static FileWriter logWriterGB;
	private static byte[] dataGB;
    public static void main(String[] args) {

    	argsGB = args;
        mainHelper();
        System.out.println("server: shutdown");
    }

	private static void mainHelper() {
		// TODO Auto-generated method stub
		java.util.Date date= new java.util.Date();
		AudioFormat format = new AudioFormat(48000, 16, 1, true, true);
        TargetDataLine microphoneLine;
        AudioInputStream microphoneStream;
        System.out.println("Waiting!");
		try (ServerSocket serverSocker = new ServerSocket(6666)) {
            if (serverSocker.isBound()) {
                Socket client = serverSocker.accept();
                try {
                    microphoneLine = AudioSystem.getTargetDataLine(format);
										Date startTime = new Date();
										SimpleDateFormat dateForm = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
										File log = new File("." + File.separator + "flightData" + File.separator + dateForm.format(startTime) + ".log");
										FileWriter logWriter = new FileWriter(log);
										logWriterGB = logWriter;
										logWriter.write(dateForm.format(startTime) +".dat ");
										logWriter.flush();
										File dstFile = new File("." + File.separator +  "flightData" + File.separator + dateForm.format(startTime) + ".dat");
                    DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                    microphoneLine = (TargetDataLine) AudioSystem.getLine(info);
                    microphoneLine.open(format);
                    microphoneStream = new AudioInputStream(microphoneLine);
                    micGB = microphoneStream;
										FileOutputStream archive = new FileOutputStream(dstFile);
										archiveGB = archive;
                    DataOutputStream out = new DataOutputStream(client.getOutputStream());
                    outGB = out;
                    byte[] data = new byte[16000];
										dataGB = data;
                    microphoneLine.start();
                    System.out.println("Starting!");
                    int count = 0;
                    while ((microphoneStream.read(data)) != -1) {
                    	//numBytesRead = microphoneStream.read(data);
                        //bytesRead += numBytesRead;
                        // write the mic data to a stream for use later
												archive.write(data, 0, data.length);
                        out.write(data, 0, data.length);
												count += data.length;
												if(count >= 48000*10*2)
												{
													archive.close();
													Date newTime = new Date();
													logWriter.write(dateForm.format(newTime) +".dat ");
													logWriter.flush();
													dstFile = new File("." + File.separator + "flightData" + File.separator + dateForm.format(newTime) + ".dat");
													archive = new FileOutputStream(dstFile);
													archiveGB = archive;
													count = 0;
												}
                    }
										logWriter.close();
                    out.flush();
                    out.close();
										archive.close();
                    microphoneLine.close();
                }catch (LineUnavailableException e) {
                    e.printStackTrace();
                }
            }
        }
        catch (SocketException e){
            try {
							logWriterGB.close();
            	System.out.println("Crashing! Still storing.");
							while ((micGB.read(dataGB)) != -1) {
								//numBytesRead = microphoneStream.read(data);
									//bytesRead += numBytesRead;
									// write the mic data to a stream for use later
									archiveGB.write(dataGB, 0, dataGB.length);
							}
							System.out.println("Exiting");
							micGB.close();
							outGB.flush();
            	outGB.close();
							archiveGB.flush();
							archiveGB.close();
							main(argsGB);

			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
        	main(argsGB);
        }
        catch (IOException e){
        	e.printStackTrace();
        }
	}
}


/*
imp ort java.io.*;
imp ort java.net.*;

public class AudioServer {
    public static void main(String[] args) throws IOException {
        if (args.length == 0)
            throw new IllegalArgumentException("expected sound file arg");
        File soundFile = AudioUtil.getSoundFile(args[0]);

        System.out.println("server: " + soundFile);

        try (ServerSocket serverSocker = new ServerSocket(6666);
            FileInputStream in = new FileInputStream(soundFile)) {
            if (serverSocker.isBound()) {
                Socket client = serverSocker.accept();
                OutputStream out = client.getOutputStream();

                byte buffer[] = new byte[2048];
                int count;
                while ((count = in.read(buffer)) != -1)
                    out.write(buffer, 0, count);
            }
        }

        System.out.println("server: shutdown");
    }
}
*/
