package br.gadobot.handlers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.tuple.Pair;

import br.gadobot.InitApp;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.specification.Album;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRefreshRequest;
import se.michaelthelin.spotify.requests.data.albums.GetAlbumRequest;
import se.michaelthelin.spotify.requests.data.playlists.GetPlaylistRequest;
import se.michaelthelin.spotify.requests.data.playlists.GetPlaylistsItemsRequest;
import se.michaelthelin.spotify.requests.data.tracks.GetTrackRequest;

public class SpotifyHandler {
		
	
//	private static final SpotifyApi spotify = new SpotifyApi.Builder()
//			.setClientId("b4bce5a2399d4867a9178c4dc19f4157")
//			.setClientSecret("130eeaf2287d43bf9230114c4ab38459")
//			.build();
	
	private static final SpotifyApi SPOTIFY_API;
	
	private static String spotifyAccessToken, spotifyRefreshToken;
	private static final String CLIENT_ID, CLIENT_SECRET;
	private static ExecutorService executor = Executors.newCachedThreadPool();
	
	private static Timer timer = new Timer();
	private static TimerTask task = new TimerTask() {
		public void run() {
			refreshSpotifyToken();
		}
	};
	
	private static File cfgToken = new File(System.getProperty("user.dir") + InitApp.FILE_SEPARATOR + "cfg.txt");
	private static File cfgClient = new File(System.getProperty("user.dir") + InitApp.FILE_SEPARATOR + "spotify_cfg.txt");
	
	public SpotifyHandler() {}
	
	static {
		if (!cfgToken.exists())
			try {
				cfgToken.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}			
		Pair<String, String> clientConfigs = getSpotifyConfigs().getLeft();
		Pair<String, String> tokenConfigs = getSpotifyConfigs().getRight();
		
		CLIENT_ID = clientConfigs.getLeft();
		CLIENT_SECRET = clientConfigs.getRight();
		
		SPOTIFY_API = new SpotifyApi.Builder().setClientId(CLIENT_ID).setClientSecret(CLIENT_SECRET).build();
		
		spotifyAccessToken = tokenConfigs.getLeft();
		spotifyRefreshToken = tokenConfigs.getRight();
				
		SPOTIFY_API.setAccessToken(spotifyAccessToken);
		SPOTIFY_API.setRefreshToken(spotifyRefreshToken);
		
		String time = formatTime();
		
		InitApp.LOGGER.info("Time of start: " + time);
		
		timer.scheduleAtFixedRate(task, 0, 30 * 60 * 1000);
	}
	
	private static Pair<Pair<String, String>, Pair<String, String>> getSpotifyConfigs() {
		
		String clientId = "", clientSecret = "", accessToken = "", refreshToken = "";
		
		try(BufferedReader brClient = new BufferedReader(new FileReader(cfgClient));
			BufferedReader brToken = new BufferedReader(new FileReader(cfgToken))) {
				
			clientId = brClient.readLine();
			clientSecret = brClient.readLine();
			
			accessToken = brToken.readLine();
			refreshToken = brToken.readLine();

		} catch (IOException e) {
			e.printStackTrace();
		}
		return Pair.of(Pair.of(clientId, clientSecret), Pair.of(accessToken, refreshToken));
	}
	
	private static String formatTime() {
		String hour = String.valueOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
		String minute = String.valueOf(Calendar.getInstance().get(Calendar.MINUTE));
		minute = minute.length() < 2 ? ("0" + minute) : minute;
		String time = hour + ":" + minute;
		return time;
	}
	
	public static Future<String> trackConverterAsync(String trackUrl) {
		
		GetTrackRequest trackRequest;
		
		if (!trackUrl.startsWith("id:")) {
			trackRequest = SPOTIFY_API.getTrack(trackUrl.contains("?") ? trackUrl.split("/")[4].split("\\?")[0] : trackUrl.split("/")[4]).build();
		} else {
			trackRequest = SPOTIFY_API.getTrack(trackUrl.substring(3, trackUrl.length())).build();
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
	
	public static int getNumberOfTracksAlbum(String albumUrl) {
		String parsedId = albumUrl.contains("?") ? albumUrl.split("/")[4].split("\\?")[0] : albumUrl.split("/")[4];
		
		GetAlbumRequest albumRequest = SPOTIFY_API.getAlbum(parsedId).build();
		
		int nOfTracks = 0;
		
		try {
			Album album = albumRequest.execute();
			return nOfTracks = album.getTracks().getTotal();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return nOfTracks;
	}
	
	public static int getNumberOfTracks(String playlistUrl) {
		
		String parsedId = playlistUrl.contains("?") ? playlistUrl.split("/")[4].split("\\?")[0] : playlistUrl.split("/")[4];
		
		GetPlaylistRequest playlistRequest = SPOTIFY_API.getPlaylist(parsedId).build();
		
		int nOfTracks = 0;
		
		try {
			Playlist playlist = playlistRequest.execute();
			nOfTracks = playlist.getTracks().getTotal();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return nOfTracks;
	}
	
	public static Future<String> firstTrackConverterAsync(String playlistUrl) {
		
		return executor.submit(() -> {
		
		String fullInfo = "";

		String parsedId = playlistUrl.contains("?") ? playlistUrl.split("/")[4].split("\\?")[0] : playlistUrl.split("/")[4];
		
		GetPlaylistRequest playlistRequest = SPOTIFY_API.getPlaylist(parsedId).build();
		
		try {
			
			Playlist playlist = playlistRequest.execute();
			Track trackRequest = SPOTIFY_API.getTrack(playlist.getTracks().getItems()[0].getTrack().getId()).build().execute();
						
			fullInfo = trackRequest.getArtists()[0].getName() + " - " + trackRequest.getName();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return fullInfo;
		});
	}
	
	public static Future<List<String>> playlistConverterAsync(String playlistUrl) {

		int nOfTracks = getNumberOfTracks(playlistUrl);
		List<String> tracks = new LinkedList<>();

		if (nOfTracks > 100) {
			return executor.submit(() -> {
				Double nOfLoops = Math.ceil(nOfTracks / 100d);

				String parsedId = playlistUrl.contains("?") ? playlistUrl.split("/")[4].split("\\?")[0] : playlistUrl.split("/")[4];
				for (int i = 0; i < nOfLoops.intValue(); i++) {
					GetPlaylistsItemsRequest itemsRequest = SPOTIFY_API.getPlaylistsItems(parsedId.trim()).offset(i*100).build();
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
				GetPlaylistRequest playlistRequest = SPOTIFY_API.getPlaylist(parsedId.trim()).build();

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
	
	public static Future<Track> trackConverterId(String id) {
		GetTrackRequest trackRequest = SPOTIFY_API.getTrack(id).build();		
		return executor.submit(() -> {
			try {
				return trackRequest.execute();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		});
	}
	
	public static Future<List<String>> albumConverterAsync(String albumUrl) {
		
		List<String> tracks = new LinkedList<>();

		GetAlbumRequest items = SPOTIFY_API.getAlbum(albumUrl).build();

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

		try (BufferedWriter br = new BufferedWriter(new FileWriter(cfgToken))) {
			
			AuthorizationCodeRefreshRequest authorizationCodeRefreshRequest = SPOTIFY_API.authorizationCodeRefresh().build();
			final AuthorizationCodeCredentials authorizationCodeCredentials = authorizationCodeRefreshRequest.execute();
			
			br.write(authorizationCodeCredentials.getAccessToken());
			br.write(System.lineSeparator());
			br.write(spotifyRefreshToken);
			
			SPOTIFY_API.setAccessToken(authorizationCodeCredentials.getAccessToken());
			
			String time =  formatTime();
			
			InitApp.LOGGER.info("Token successfully refreshed at: " + time);
			InitApp.LOGGER.info("Expires in: " + authorizationCodeCredentials.getExpiresIn() + " s");
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
