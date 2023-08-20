package jdos.win.system;

import jdos.util.Log;
import jdos.win.utils.FilePath;
import org.apache.logging.log4j.Level;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;

public class WinMidi extends WinMCI {
    static public WinMidi create() {
        return new WinMidi(nextObjectId());
    }

    private FilePath file;
    private Sequence sequence;
    private Sequencer sequencer;

    public WinMidi(int id) {
        super(id);
    }

    public void play(int from, int to, int hWndCallback, boolean wait) {
        hWnd = hWndCallback;
        sequencer.start();
    }

    public void stop(int hWndCallback, boolean wait) {
        if (sequencer != null)
            sequencer.stop();
        hWnd = hWndCallback;
        if (hWnd != 0)
            sendNotification(MCI_NOTIFY_SUCCESSFUL);
    }

    public void close(int hWndCallback, boolean wait) {
        if (sequencer != null)
            sequencer.close();
        hWnd = hWndCallback;
        if (hWnd != 0)
            sendNotification(MCI_NOTIFY_SUCCESSFUL);
    }

    public boolean setFile(FilePath file) {
        this.file = file;
        try {
            sequence = MidiSystem.getSequence(file.getInputStream());
            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequencer.setSequence(sequence);
            sequencer.addMetaEventListener(meta -> {
                if ( meta.getType() == 47 ) {
                    if (hWnd != 0)
                        sendNotification(MCI_NOTIFY_SUCCESSFUL);
                }
            });
            return true;
        } catch (Exception e) {
            Log.getLogger().log(Level.ERROR, "Could not set file: ", e);
            return false;
        }
    }

}
