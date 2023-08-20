package jdos.hardware;

import jdos.util.Log;
import jdos.misc.Program;
import org.apache.logging.log4j.Level;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

public class AudioLayer {
    static private byte[] audioBuffer;
    static public SourceDataLine line;
    static private boolean audioThreadExit = false;


    static private Thread audioThread;

    public static boolean open(int bufferSize, int freq) {
        AudioFormat format = new AudioFormat(freq, 16, 2, true, false);
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, bufferSize);
            line.start();
            audioThreadExit = false;
            audioThread = new Thread(() -> {
                while (!audioThreadExit) {
                    boolean result;
                    synchronized (Mixer.audioMutex) {
                        result = Mixer.MIXER_CallBack(0, audioBuffer, audioBuffer.length);
                    }
                    if (result)
                        line.write(audioBuffer, 0, audioBuffer.length);
                    else {
                        try {Thread.sleep(20);} catch (Exception e) {
                            Log.getLogger().log(Level.ERROR, "Runtime error: ", e);
                        }
                    }
                }
            });
            audioBuffer = new byte[512]; // this needs to be smaller than buffer size passed into open other line.write will block
            audioThread.start();
            return true;
        } catch (Exception e) {
            Log.getLogger().log(Level.ERROR, "Could not open audio buffer: ", e);
            return false;
        }
    }

    public static void stop() {
        audioThreadExit = true;
        try {audioThread.join(2000);} catch (Exception e) {
            Log.getLogger().log(Level.ERROR, "Runtime error: ", e);
        }
        line.drain();
        line.stop();
    }

    public static void listMidi(Program program) {
        MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();

        for (int i=0;i<devices.length;i++) {
            program.WriteOut("%2d\t \"%s\"\n",new Object[]{i,devices[i].getName()});
        }
    }
}
