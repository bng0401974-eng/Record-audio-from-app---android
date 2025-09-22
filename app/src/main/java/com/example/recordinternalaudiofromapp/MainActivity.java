package com.example.recordinternalaudiofromapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Mp3RecordPlay";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 201;
    private static final int DEFAULT_SAMPLE_RATE = 44100;
    private static final int DEFAULT_CHANNELS = 1;
    private static final int DEFAULT_BIT_DEPTH = 8;

    private MediaPlayer mediaPlayer;
    private Visualizer visualizer;
    private FileOutputStream recordingOutputStream;
    private File recordingFile;
    private File wavFile;

    private Button btnPlayAndRecordMp3, btnStopMp3;
    private TextView tvStatusMp3;

    private boolean isPlayingAndRecording = false;
    private Handler mainThreadHandler;
    private int actualCaptureSampleRate = DEFAULT_SAMPLE_RATE;
    private boolean permissionRequestedInOnCreate = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Ensure this layout exists

        mainThreadHandler = new Handler(Looper.getMainLooper());

        btnPlayAndRecordMp3 = findViewById(R.id.btnPlayAndRecordMp3);
        btnStopMp3 = findViewById(R.id.btnStopMp3);
        tvStatusMp3 = findViewById(R.id.tvStatusMp3);

        if (savedInstanceState != null) {
            permissionRequestedInOnCreate = savedInstanceState.getBoolean("permissionRequestedInOnCreate", false);
        }

        if (!permissionRequestedInOnCreate) {
            checkAndRequestAudioPermission();
            permissionRequestedInOnCreate = true;
        }

        btnPlayAndRecordMp3.setOnClickListener(v -> {
            Log.d(TAG, "Play & Record button clicked.");
            if (checkPermission()) {
                Log.d(TAG, "RECORD_AUDIO permission is granted (button click).");
                startPlayingAndRecordingMp3();
            } else {
                Log.w(TAG, "RECORD_AUDIO permission NOT granted (button click). Prompting again or showing rationale.");
                checkAndRequestAudioPermission();
            }
        });

        btnStopMp3.setOnClickListener(v -> {
            Log.d(TAG, "Stop button clicked.");
            stopPlayingAndRecordingMp3();
        });

        String pcmFileName = "recorded_mp3_audio.pcm";
        String finalWavFileName = "recorded_mp3_audio.wav";
        File externalDir = getExternalFilesDir(null);
        if (externalDir != null) {
            recordingFile = new File(externalDir, pcmFileName);
            wavFile = new File(externalDir, finalWavFileName);
        } else {
            Log.e(TAG, "External files directory is null. Cannot create recording files.");
            Toast.makeText(this, "Storage error. Cannot create recording files.", Toast.LENGTH_LONG).show();
            // Disable button or handle error appropriately
            btnPlayAndRecordMp3.setEnabled(false);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("permissionRequestedInOnCreate", permissionRequestedInOnCreate);
    }

    private void checkAndRequestAudioPermission() {
        if (checkPermission()) {
            Log.d(TAG, "RECORD_AUDIO permission already granted (checkAndRequest).");
        } else {
            Log.d(TAG, "RECORD_AUDIO permission not granted. Requesting (checkAndRequest)...");
            requestPermission();
        }
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            Log.i(TAG, "Showing rationale for RECORD_AUDIO permission.");
            new AlertDialog.Builder(this)
                    .setTitle("Permission Needed")
                    .setMessage("This app needs the Record Audio permission to capture the internal audio of the MP3 being played.")
                    .setPositiveButton("OK", (dialog, which) -> {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.RECORD_AUDIO},
                                REQUEST_RECORD_AUDIO_PERMISSION);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        Log.w(TAG, "User cancelled RECORD_AUDIO permission rationale dialog.");
                        Toast.makeText(MainActivity.this, "Record Audio permission is required.", Toast.LENGTH_LONG).show();
                    })
                    .create().show();
        } else {
            Log.d(TAG, "No rationale needed, requesting RECORD_AUDIO permission directly.");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "RECORD_AUDIO permission GRANTED by user.");
                Toast.makeText(this, "Record Audio permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "RECORD_AUDIO permission DENIED by user.");
                Toast.makeText(this, "RECORD_AUDIO permission denied. Cannot record.", Toast.LENGTH_LONG).show();
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                    Log.w(TAG, "RECORD_AUDIO permission permanently denied. Guiding to settings.");
                    new AlertDialog.Builder(this)
                            .setTitle("Permission Permanently Denied")
                            .setMessage("Record Audio permission is required. Please enable it in App Settings.")
                            .setPositiveButton("Go to Settings", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .create().show();
                }
            }
        }
    }

    private void startPlayingAndRecordingMp3() {
        if (isPlayingAndRecording) {
            Log.d(TAG, "Already playing and recording. Ignoring request.");
            Toast.makeText(this, "Already playing and recording.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!checkPermission()) {
            Log.e(TAG, "Attempted to start recording without permission!");
            Toast.makeText(this, "RECORD_AUDIO permission is required. Please grant it.", Toast.LENGTH_LONG).show();
            checkAndRequestAudioPermission();
            return;
        }
        if (recordingFile == null || wavFile == null) {
            Log.e(TAG, "Recording file paths are not initialized. Cannot start recording.");
            Toast.makeText(this, "Storage error. Cannot initialize recording files.", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "Attempting to start playing and recording MP3...");

        // Release previous MediaPlayer instance if it exists
        releaseMediaPlayer(); // Ensures a clean state

        mediaPlayer = new MediaPlayer(); // State: Idle

        try {
            // Prepare recording output stream (PCM)
            if (recordingFile.exists()) {
                if (!recordingFile.delete()) {
                    Log.w(TAG, "Could not delete existing PCM file.");
                }
            }
            recordingOutputStream = new FileOutputStream(recordingFile);
            Log.d(TAG, "Recording PCM file stream opened: " + recordingFile.getAbsolutePath());

            // Set audio attributes
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );

            // Set Data Source (from raw resource)
            AssetFileDescriptor afd = null;
            try {
                afd = getResources().openRawResourceFd(R.raw.my_song); // Ensure R.raw.my_song exists
                if (afd == null) {
                    Log.e(TAG, "Raw resource R.raw.my_song not found.");
                    Toast.makeText(this, "Audio file not found in resources.", Toast.LENGTH_LONG).show();
                    closeRecordingStream(); // Close stream if opened
                    releaseMediaPlayer();   // Release player
                    return;
                }
                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            } catch (Resources.NotFoundException e) {
                Log.e(TAG, "Raw resource not found exception: " + R.raw.my_song, e);
                Toast.makeText(this, "Audio resource error.", Toast.LENGTH_LONG).show();
                closeRecordingStream();
                releaseMediaPlayer();
                return;
            } finally {
                if (afd != null) {
                    try {
                        afd.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing AssetFileDescriptor: " + e.getMessage());
                    }
                }
            }
            // MediaPlayer state: INITIALIZED

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer Error! What: " + what + ", Extra: " + extra);
                Toast.makeText(MainActivity.this, "MediaPlayer error occurred. What: " + what, Toast.LENGTH_LONG).show();
                stopPlayingAndRecordingMp3(); // Use your existing stop method to clean up everything
                return true;
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "MediaPlayer playback completed.");
                mainThreadHandler.postDelayed(this::stopPlayingAndRecordingMp3, 250);
            });

            mediaPlayer.setOnPreparedListener(mp -> {
                Log.i(TAG, "MediaPlayer prepared. Audio Session ID: " + mp.getAudioSessionId());
                try {
                    mp.start(); // Start actual playback
                    Log.i(TAG, "MediaPlayer started.");

                    // Setup and enable Visualizer AFTER MediaPlayer has started
                    if (!setupVisualizerAndEnable(mp.getAudioSessionId())) {
                        Log.e(TAG, "Visualizer setup/enable failed after MediaPlayer prepared.");
                        Toast.makeText(MainActivity.this, "Visualizer failed. Recording may not work.", Toast.LENGTH_LONG).show();
                        // Continue playback but recording might fail, or stop everything:
                        // stopPlayingAndRecordingMp3();
                        // return;
                    } else {
                        Log.i(TAG, "Visualizer setup and enabled.");
                    }

                    isPlayingAndRecording = true;
                    runOnUiThread(() -> {
                        btnPlayAndRecordMp3.setEnabled(false);
                        btnStopMp3.setEnabled(true);
                        tvStatusMp3.setText("Status: Playing MP3 & Recording...");
                    });

                } catch (IllegalStateException e) {
                    Log.e(TAG, "IllegalStateException when starting MediaPlayer post-prepare or enabling Visualizer: " + e.getMessage(), e);
                    Toast.makeText(MainActivity.this, "Error starting playback: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    stopPlayingAndRecordingMp3(); // Clean up on error
                }
            });

            Log.d(TAG, "MediaPlayer calling prepareAsync()...");
            mediaPlayer.prepareAsync(); // State: PREPARING

        } catch (IOException e) {
            Log.e(TAG, "IOException during MediaPlayer setDataSource or FileOutputStream: " + e.getMessage(), e);
            Toast.makeText(this, "File or Audio source error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            closeRecordingStream();
            releaseMediaPlayer();
            resetUI();
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException during MediaPlayer setup (before prepareAsync): " + e.getMessage(), e);
            Toast.makeText(this, "MediaPlayer state error during setup: " + e.getMessage(), Toast.LENGTH_LONG).show();
            closeRecordingStream();
            releaseMediaPlayer();
            resetUI();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException during MediaPlayer setup: " + e.getMessage(), e);
            Toast.makeText(this, "Invalid argument for MediaPlayer: " + e.getMessage(), Toast.LENGTH_LONG).show();
            closeRecordingStream();
            releaseMediaPlayer();
            resetUI();
        }
    }

    private boolean setupVisualizerAndEnable(int audioSessionId) {
        Log.d(TAG, "Setting up Visualizer for session ID: " + audioSessionId);
        try {
            releaseVisualizer(); // Release previous instance if any

            if (audioSessionId == 0 ) { // MediaPlayer.AUDIO_SESSION_ID_GENERATE is 0
                Log.w(TAG, "Audio session ID is 0. Visualizer might not attach correctly or capture any audio.");
                // Depending on strictness, you could return false here.
                // However, sometimes it might get a valid session later.
            }

            visualizer = new Visualizer(audioSessionId);
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer viz, byte[] waveform, int samplingRate) {
                    actualCaptureSampleRate = samplingRate;
                    if (isPlayingAndRecording && recordingOutputStream != null && waveform != null) {
                        try {
                            recordingOutputStream.write(waveform);
                        } catch (IOException e) {
                            Log.e(TAG, "Error writing waveform data: " + e.getMessage());
                        }
                    }
                }
                @Override
                public void onFftDataCapture(Visualizer viz, byte[] fft, int samplingRate) {}
            }, Visualizer.getMaxCaptureRate() / 2, true, false);

            int vizInitState = visualizer.setEnabled(true); // Enable it immediately after setup
            if (vizInitState == Visualizer.SUCCESS) {
                Log.i(TAG, "Visualizer enabled successfully in setup.");
                return true;
            } else {
                Log.e(TAG, "Failed to enable Visualizer in setup. State: " + vizInitState);
                releaseVisualizer(); // Clean up if enabling failed
                return false;
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException setting up/enabling Visualizer: " + e.getMessage(), e);
        } catch (UnsupportedOperationException e) {
            Log.e(TAG, "Visualizer UNSUPPORTED on this device/session: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            Log.e(TAG, "RuntimeException setting up/enabling Visualizer: " + e.getMessage(), e);
        }
        Toast.makeText(this, "Visualizer setup failed.", Toast.LENGTH_LONG).show();
        releaseVisualizer();
        return false;
    }

    private void stopPlayingAndRecordingMp3() {
        if (!isPlayingAndRecording && mediaPlayer == null && visualizer == null && recordingOutputStream == null) {
            Log.d(TAG, "Already stopped or not initialized.");
            // Make sure UI is reset if it wasn't already
            if (btnPlayAndRecordMp3 != null && !btnPlayAndRecordMp3.isEnabled()) {
                resetUI();
            }
            return;
        }
        Log.i(TAG, "Stopping playback and recording...");
        isPlayingAndRecording = false;

        releaseVisualizer();
        releaseMediaPlayer();
        closeRecordingStream();

        if (recordingFile != null && recordingFile.exists() && recordingFile.length() > 0 && wavFile != null) {
            Log.d(TAG, "PCM file exists. Attempting to create WAV file.");
            try {
                if (wavFile.exists()) {
                    if (!wavFile.delete()) {
                        Log.w(TAG, "Could not delete existing WAV file.");
                    }
                }
                int sampleRateForWav = (actualCaptureSampleRate >= 8000 && actualCaptureSampleRate <= 48000) ? actualCaptureSampleRate : DEFAULT_SAMPLE_RATE;
                addWavHeader(recordingFile, wavFile, sampleRateForWav, DEFAULT_CHANNELS, DEFAULT_BIT_DEPTH);
                Log.i(TAG, "WAV file created: " + wavFile.getAbsolutePath() + " with sample rate: " + sampleRateForWav);
                // recordingFile.delete(); // Optionally delete PCM
            } catch (IOException e) {
                Log.e(TAG, "Error creating WAV file: " + e.getMessage(), e);
                Toast.makeText(this, "Could not create WAV file.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.w(TAG, "PCM file is empty or does not exist, or wavFile path is null. Skipping WAV creation.");
        }
        resetUI();
        Toast.makeText(this, "Stopped. Recording saved (if successful).", Toast.LENGTH_LONG).show();
        // In stopPlayingAndRecordingMp3() after successful WAV creation:
        Log.i(TAG, "WAV file created: " + wavFile.getAbsolutePath() );

    }


    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
                Log.d(TAG, "MediaPlayer released.");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaPlayer: " + e.getMessage());
            }
            mediaPlayer = null;
        }
    }

    private void releaseVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false); // Ensure it's disabled before release
                visualizer.release();
                Log.d(TAG, "Visualizer released.");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing Visualizer: " + e.getMessage());
            }
            visualizer = null;
        }
    }

    private void closeRecordingStream() {
        if (recordingOutputStream != null) {
            try {
                recordingOutputStream.flush();
                recordingOutputStream.close();
                Log.d(TAG, "Recording PCM output stream flushed and closed.");
            } catch (IOException e) {
                Log.e(TAG, "Error closing recording PCM stream: " + e.getMessage());
            }
            recordingOutputStream = null;
        }
    }

    private void resetUI() {
        runOnUiThread(() -> {
            if (btnPlayAndRecordMp3 != null) btnPlayAndRecordMp3.setEnabled(true);
            if (btnStopMp3 != null) btnStopMp3.setEnabled(false);
            if (tvStatusMp3 != null) tvStatusMp3.setText("Status: Idle");
            Log.d(TAG, "UI reset to Idle state.");
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop called. If playing, stopping and releasing resources.");
        // If the app is stopped and was playing, ensure cleanup.
        if (isPlayingAndRecording || mediaPlayer != null) {
            stopPlayingAndRecordingMp3(); // This will handle release of all components
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called. Ensuring all resources are released.");
        // Should have been handled by onStop if activity was visible then stopped
        // But this is a final safeguard.
        releaseVisualizer();
        releaseMediaPlayer();
        closeRecordingStream();
    }

    private void addWavHeader(File pcmFile, File wavFileOutput, int sampleRate, int channels, int bitDepth) throws IOException {
        long audioDataLength = pcmFile.length();
        if (audioDataLength == 0) throw new IOException("PCM file is empty.");

        long overallDataLength = audioDataLength + 36;
        int bytesPerSample = bitDepth / 8;
        long byteRate = (long) sampleRate * channels * bytesPerSample;
        int blockAlign = channels * bytesPerSample;
        byte[] header = new byte[44];

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (overallDataLength & 0xff);
        header[5] = (byte) ((overallDataLength >> 8) & 0xff);
        header[6] = (byte) ((overallDataLength >> 16) & 0xff);
        header[7] = (byte) ((overallDataLength >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) blockAlign; header[33] = 0;
        header[34] = (byte) bitDepth; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (audioDataLength & 0xff);
        header[41] = (byte) ((audioDataLength >> 8) & 0xff);
        header[42] = (byte) ((audioDataLength >> 16) & 0xff);
        header[43] = (byte) ((audioDataLength >> 24) & 0xff);

        try (FileOutputStream fos = new FileOutputStream(wavFileOutput);
             FileInputStream fis = new FileInputStream(pcmFile)) {
            fos.write(header, 0, 44);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
        Log.d(TAG, "WAV header added. Output: " + wavFileOutput.getAbsolutePath());
    }
}
