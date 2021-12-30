package br.gadobot.Handlers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.Track;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;
import com.wrapper.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERefreshRequest;
import com.wrapper.spotify.requests.data.albums.GetAlbumRequest;
import com.wrapper.spotify.requests.data.playlists.GetPlaylistRequest;
import com.wrapper.spotify.requests.data.playlists.GetPlaylistsItemsRequest;
import com.wrapper.spotify.requests.data.tracks.GetTrackRequest;

import br.gadobot.Gado;

public class SpotifyHandler {
		
	private static final SpotifyApi spotify = new SpotifyApi.Builder()
			.setClientId("d7aee843f7eb4b5394240745cf729a76")
			.setClientSecret("834f3cb65fbc474eaa04a6224bcf5cf4")
			.build();
	
	private static String spotifyAccessToken;
	private static String spotifyRefreshToken;
	private static ExecutorService executor = Executors.newCachedThreadPool();
	
	private static Timer timer = new Timer();
	private static TimerTask task = new TimerTask() {
		public void run() {
			refreshSpotifyToken();
		}
	};
	
	private static File cfg = new File(".\\cfg.txt");
	
	public SpotifyHandler() {
		if (!cfg.exists())
			try {
				cfg.createNewFile();
			} catch (IOException e1) {
				e1.printStackTrace();
			}			
		try {

			Scanner scan = new Scanner(cfg);

			spotifyAccessToken = scan.nextLine();
			spotifyRefreshToken = scan.nextLine();
			scan.close();

			spotify.setAccessToken(spotifyAccessToken);
			spotify.setRefreshToken(spotifyRefreshToken);
			
			String time = formatTime();
			
			Gado.LOGGER.info("Time of start: " + time);
			
			timer.scheduleAtFixedRate(task, 0, 30 * 60 * 1000);

		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private static String formatTime() {
		String hour = String.valueOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
		String minute = String.valueOf(Calendar.getInstance().get(Calendar.MINUTE));
		minute = minute.length() < 2 ? ("0" + minute) : minute;
		String time = hour + ":" + minute;
		return time;
	}
	
	public Future<String> trackConverterAsync(String trackUrl) {
		
		GetTrackRequest trackRequest;
		
		if (!trackUrl.startsWith("id:")) {
			trackRequest = spotify.getTrack(trackUrl.contains("?") ? trackUrl.split("/")[4].split("\\?")[0] : trackUrl.split("/")[4]).build();
		} else {
			trackRequest = spotify.getTrack(trackUrl.substring(3, trackUrl.length())).build();
		}
				
		return executor.submit(() -> {
			try {
				Track track = trackRequest.execute();
				String name = track.getName();
				String artist = track.getArtists()[0].getName();
				return artist + " - " + name;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		});		
	}
	
	public int getNumberOfTracksAlbum(String albumUrl) {
		String parsedId = albumUrl.contains("?") ? albumUrl.split("/")[4].split("\\?")[0] : albumUrl.split("/")[4];
		
		GetAlbumRequest albumRequest = spotify.getAlbum(parsedId).build();
		
		int nOfTracks = 0;
		
		try {
			Album album = albumRequest.execute();
			return nOfTracks = album.getTracks().getTotal();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return nOfTracks;
	}
	
	public int getNumberOfTracks(String playlistUrl) {
		
		String parsedId = playlistUrl.contains("?") ? playlistUrl.split("/")[4].split("\\?")[0] : playlistUrl.split("/")[4];
		
		GetPlaylistRequest playlistRequest = spotify.getPlaylist(parsedId).build();
		
		int nOfTracks = 0;
		
		try {
			Playlist playlist = playlistRequest.execute();
			nOfTracks = playlist.getTracks().getTotal();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return nOfTracks;
	}
	
	public Future<String> firstTrackConverterAsync(String playlistUrl) {
		
		return executor.submit(() -> {
		
		String fullInfo = "";

		String parsedId = playlistUrl.contains("?") ? playlistUrl.split("/")[4].split("\\?")[0] : playlistUrl.split("/")[4];
		
		GetPlaylistRequest playlistRequest = spotify.getPlaylist(parsedId).build();
		
		try {
			
			Playlist playlist = playlistRequest.execute();
			Track trackRequest = spotify.getTrack(playlist.getTracks().getItems()[0].getTrack().getId()).build().execute();
						
			fullInfo = trackRequest.getArtists()[0].getName() + " - " + trackRequest.getName();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return fullInfo;
		});
	}
	
	public Future<List<String>> playlistConverterAsync(String playlistUrl) {

		int nOfTracks = getNumberOfTracks(playlistUrl);
		List<String> tracks = new LinkedList<>();

		if (nOfTracks > 100) {
			return executor.submit(() -> {
				Double nOfLoops = Math.ceil(nOfTracks / 100d);

				String parsedId = playlistUrl.contains("?") ? playlistUrl.split("/")[4].split("\\?")[0] : playlistUrl.split("/")[4];
				for (int i = 0; i < nOfLoops.intValue(); i++) {
					GetPlaylistsItemsRequest itemsRequest = spotify.getPlaylistsItems(parsedId.trim()).offset(i*100).build();
					try {
						Paging<PlaylistTrack> playlistTracks = itemsRequest.execute();
						for (int j = 0; j < 100; j++) {
							if (i == 0 && j == 0) continue;
							else {
								if (i == nOfLoops.intValue()-1 && j == playlistTracks.getItems().length) return tracks;
								Track plTrack = (Track) playlistTracks.getItems()[j].getTrack();
								tracks.add(plTrack.getArtists()[0].getName() + " - " + plTrack.getName());
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				return tracks;
			});
			
		} else {
			return executor.submit(() -> {
				String parsedId = playlistUrl.contains("?") ? playlistUrl.split("/")[4].split("\\?")[0] : playlistUrl.split("/")[4];
				GetPlaylistRequest playlistRequest = spotify.getPlaylist(parsedId.trim()).build();

				try {
					Playlist playlist = playlistRequest.execute();
					Track playlistTrack;
					for (int i = 1; i < playlist.getTracks().getTotal(); i++) {
						playlistTrack = (Track) playlist.getTracks().getItems()[i].getTrack();
						tracks.add(playlistTrack.getArtists()[0].getName() + " - " + playlistTrack.getName());
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				return tracks;
			});
		}
	}
	
	public Future<Track> trackConverterId(String id) {
		GetTrackRequest trackRequest = spotify.getTrack(id).build();		
		return executor.submit(() -> {
			try {
				return trackRequest.execute();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		});
	}
	
	public Future<List<String>> albumConverterAsync(String albumUrl) {
		
		List<String> tracks = new LinkedList<>();

		GetAlbumRequest items = spotify.getAlbum(albumUrl).build();

		return executor.submit(() -> {

			try {
				Album album = items.execute();
				TrackSimplified simpTrack;
				
				for (int i = 0; i < album.getTracks().getTotal(); i++) {
					simpTrack = album.getTracks().getItems()[i];
					tracks.add(simpTrack.getArtists()[0] + " - " + simpTrack.getName());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			return tracks;
		});
	}
	
	public static void refreshSpotifyToken() {

		try {
			AuthorizationCodePKCERefreshRequest authorizationCodePKCERefreshRequest = spotify.authorizationCodePKCERefresh().build();
			final AuthorizationCodeCredentials authorizationCodeCredentials = authorizationCodePKCERefreshRequest.execute();
			
			BufferedWriter br = new BufferedWriter(new FileWriter(cfg));
			br.write(authorizationCodeCredentials.getAccessToken());
			br.write("\n");
			br.write(authorizationCodeCredentials.getRefreshToken());
			br.close();
			
			spotify.setAccessToken(authorizationCodeCredentials.getAccessToken());
			spotify.setRefreshToken(authorizationCodeCredentials.getRefreshToken());
			
			String time =  formatTime();
			
			Gado.LOGGER.info("Token successfully refreshed at: " + time);
			Gado.LOGGER.info("Expires in: " + authorizationCodeCredentials.getExpiresIn() + " s");
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
