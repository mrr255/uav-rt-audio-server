
import java.io.*;
import java.util.*;
import javax.sound.sampled.*;

public class AudioProcess
{

  public AudioProcess(String[] args)
  {
    if(args.length != 1)
    {
      System.out.println("error: need file of files");
      return;
    }
    File log = new File(args[0]); // Log has list of data files
    try
    {
      Scanner logr = new Scanner(log);
      byte[] dat = new byte[0];
      while(logr.hasNext())
      {	//Read Each File Name
        String curFName = logr.next();
        RandomAccessFile curF = new RandomAccessFile("." + File.separator + "flightData" + File.separator + curFName, "r"); // Open Each File
        byte[] curB = new byte[(int)curF.length()]; //Make containing byte array
        curF.readFully(curB); //Add to byte array
	      curF.close();
        //Write existing Data and New data to the array
        dat = concat(dat,curB);
      }

	//Once Done, convert Byte Array to Wav File
      AudioInputStream pcm;
      InputStream b_in = new ByteArrayInputStream(dat);
      AudioFormat format = new AudioFormat(48000, 16, 1, true, true);
      AudioInputStream source = new AudioInputStream(b_in,format , dat.length/2);
      String file = args[0].substring(0,args[0].length()-4);
      File newFile = new File(file + ".wav");
      AudioSystem.write(source, AudioFileFormat.Type.WAVE, newFile);

      source.close();
      //pcm.close();
    }
    catch (FileNotFoundException e)
    {
      System.out.println("error: need valid file name");
      System.out.println(e);
      return;
    }
    catch (IOException e)
    {
      System.out.println("error: error reading dat file");
      System.out.println(e);
      return;
    }
  }
  public static byte[] concat(byte[] a, byte[] b) {
   int aLen = a.length;
   int bLen = b.length;
   byte[] c= new byte[aLen+bLen];
   System.arraycopy(a, 0, c, 0, aLen);
   System.arraycopy(b, 0, c, aLen, bLen);
   return c;
  }
  public static void main(String[] args)
  {
    new AudioProcess(args);

  }
}
