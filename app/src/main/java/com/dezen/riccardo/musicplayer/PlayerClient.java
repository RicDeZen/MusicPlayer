package com.dezen.riccardo.musicplayer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.dezen.riccardo.musicplayer.song.Song;

import java.util.HashMap;
import java.util.Map;

/**
 * Class used to simplify MediaBrowserClient usage. Communicates with a {@link PlayerService}.
 * {@link PlayerClient#connect()} should be called when the host Activity is started.
 * {@link PlayerClient#disconnect()} should be called when the Activity is stopped.
 * The class is an Object pool, only one client is allowed for a certain Context. Context are not
 * referenced directly, their hashcode is used instead.
 *
 * @author Riccardo De Zen.
 */
public class PlayerClient {

    private static final Map<Integer, PlayerClient> instancePool = new HashMap<>();

    private final MediaBrowserCompat mediaBrowser;
    private MediaControllerCompat mediaController;
    private MediaControllerCompat.Callback controllerCallback;

    /**
     * @param context The {@link Context} hosting the media player. If it is an Activity, it will
     *                be set in the MediaController.
     */
    private PlayerClient(@NonNull Context context) {
        // Defining callbacks.
        MediaBrowserCompat.ConnectionCallback callbacks =
                new MediaBrowserCompat.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        // Will be non-null when this method is called.
                        MediaSessionCompat.Token token = mediaBrowser.getSessionToken();

                        // Initialize the media controller.
                        mediaController = new MediaControllerCompat(
                                context, token
                        );
                        if (context instanceof Activity)
                    MediaControllerCompat.setMediaController((Activity) context, mediaController);

                // Register callbacks if they have been set.
                if (controllerCallback != null) {
                    mediaController.registerCallback(controllerCallback);
                    controllerCallback.onMetadataChanged(mediaController.getMetadata());
                }
            }

            @Override
            public void onConnectionFailed() {
                super.onConnectionFailed();
                Toast.makeText(context, R.string.connection_failed_error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onConnectionSuspended() {
                super.onConnectionSuspended();
                Toast.makeText(context, R.string.service_shut_down_warning, Toast.LENGTH_LONG).show();
            }
        };

        // Initialize MediaBrowser.
        mediaBrowser = new MediaBrowserCompat(
                context,
                new ComponentName(context, PlayerService.class),
                callbacks,
                null
        );
    }

    /**
     * Retrieve the PlayerClient for a certain Context. If the Context is an Activity, the
     * MediaController in this object will be recorded as the controller for the Activity.
     *
     * @param context The calling Context.
     * @return The PlayerClient instance for the Context, if existing, or a newly created one.
     */
    public static PlayerClient of(@NonNull Context context) {
        PlayerClient instance = instancePool.get(context.hashCode());
        if (instance != null)
            return instance;
        PlayerClient newInstance = new PlayerClient(context);
        instancePool.put(context.hashCode(), newInstance);
        return newInstance;
    }

    /**
     * Connect to the Service. Should be called when the Activity is started.
     */
    public void connect() {
        mediaBrowser.connect();
    }

    /**
     * Disconnect from the Service. Should be called when the Activity is stopped.
     */
    public void disconnect() {
        mediaBrowser.disconnect();
    }

    /**
     * Registers callbacks for the media controller. If the mediaController has not been set yet,
     * the callbacks will be registered when it's available. When the callbacks are registered, the
     * metadata of the song currently being played will be provided.
     * Only one callback is allowed at a time.
     *
     * @param callback The callback for the Media Controller.
     */
    public void setListener(@NonNull MediaControllerCompat.Callback callback) {
        clearListener();
        controllerCallback = callback;
        if (mediaController != null) {
            mediaController.registerCallback(callback);
            callback.onMetadataChanged(mediaController.getMetadata());
        }
    }

    /**
     * Remove the event listener.
     */
    public void clearListener() {
        if (mediaController != null && controllerCallback != null)
            mediaController.unregisterCallback(controllerCallback);
        controllerCallback = null;
    }

    /**
     * Start playing a song on the Service. Won't do anything if the Service has not been bound yet.
     *
     * @param id String id for the Song to play.
     */
    public void play(String id) {
        if (mediaController == null)
            return;

        mediaController.getTransportControls().playFromMediaId(id, null);
    }

    /**
     * Play a Song object instead of from an id.
     *
     * @param song Song to play.
     */
    public void play(Song song) {
        play(song.getId());
    }

    /**
     * Toggle the player between playing or paused. Won't do anything if the Service has not been
     * bound yet. Either calls pause or play, so this should only be called when a Song actually is
     * already playing.
     */
    public void toggle() {
        if (mediaController == null)
            return;

        int playbackState = mediaController.getPlaybackState().getState();
        if (playbackState == PlaybackStateCompat.STATE_PLAYING)
            mediaController.getTransportControls().pause();
        else
            mediaController.getTransportControls().play();
    }
}
