package GadoBot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import javax.security.auth.login.LoginException;

import org.apache.hc.core5.http.ParseException;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.Track;
import com.wrapper.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERefreshRequest;
import com.wrapper.spotify.requests.data.albums.GetAlbumRequest;
import com.wrapper.spotify.requests.data.tracks.GetTrackRequest;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Gado extends ListenerAdapter {

	static String spotifyAccessToken = "";
	static String spotifyRefreshToken = "";
//	static final String cliendId = "YjRiY2U1YTIzOTlkNDg2N2E5MTc4YzRkYzE5ZjQxNTc=";
//	static final String clientSecret = "YTQ1MzQxMDBmYmU0NDY4ZmJkZjVlOGU3NDU1NWU4NGE=";
	
	private static final SpotifyApi spot = new SpotifyApi.Builder()
//			.setAccessToken(spotifyAccessToken)
//			.setRefreshToken(spotifyRefreshToken)
//			.setClientId(spotifyAccessToken)
//			.setClientSecret(clientSecret)
			.build();
	
	private static final AuthorizationCodePKCERefreshRequest authorizationCodePKCERefreshRequest = spot
			.authorizationCodePKCERefresh().build();
	
	public static String prefix = ";";

	private static EmbedBuilder msg;

	private static File cfg = new File("C:\\cfg.txt");
	
	public static void main(String[] args) throws LoginException, InterruptedException {
		
		try {
			
			Scanner scan = new Scanner(cfg);
			spotifyAccessToken = scan.nextLine();
			spotifyRefreshToken = scan.nextLine();
			scan.close();
			
			spot.setAccessToken(spotifyAccessToken);
			spot.setRefreshToken(spotifyRefreshToken);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		JDABuilder.create("ODkxMjkzNjQzMjMzNjk3ODMz.YU8P4w.wHgu8gfx08bw858RmdgJcx6RB3I", GatewayIntent.GUILD_MESSAGES,
				GatewayIntent.GUILD_VOICE_STATES).addEventListeners(new Gado()).build();
	}

	private final AudioPlayerManager playerManager;
	private final Map<Long, GuildMusicManager> musicManagers;

	private Gado() {
		this.musicManagers = new HashMap<>();

		this.playerManager = new DefaultAudioPlayerManager();
		AudioSourceManagers.registerRemoteSources(playerManager);
		AudioSourceManagers.registerLocalSource(playerManager);
	}

	private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
		long guildId = Long.parseLong(guild.getId());
		GuildMusicManager musicManager = musicManagers.get(guildId);

		if (musicManager == null) {
			musicManager = new GuildMusicManager(playerManager);
			musicManagers.put(guildId, musicManager);
		}

		guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

		return musicManager;
	}

	@Override
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {

		if (event.getMessage().getContentRaw().startsWith(prefix)) {

			List<String> messageParsed = Arrays.asList(event.getMessage().getContentRaw().split(" "));

			String command = messageParsed.get(0).substring(1).toLowerCase();
			String arguments = "";

			for (int i = 1; i < messageParsed.size(); i++) {
				arguments += messageParsed.get(i);
				arguments += " ";
			}
			arguments = arguments.trim();

			if (isUserInAChannel(event.getMember(), event.getGuild().getVoiceChannels())) {
				
				switch (command) {

				case "p":
				case "play":
					try {
						loadAndPlay(event.getChannel(), arguments, event.getMessage().getMember());
					} catch (Exception e) {

						msg = new EmbedBuilder();
						msg.setTitle("Ops! Um erro ocorreu!");
						msg.setDescription(
								"Mas o desenvolvedor corno vai trabalhar para consertar esse problema, por favor tente novamente");

						event.getChannel().sendMessage(msg.build()).queue();

						e.printStackTrace();
					}
					break;

				case "s":
				case "n":
				case "skip":
				case "next":
					skipTrack(event.getChannel());
					break;

				case "fq":
				case "fairqueue":
					// TODO: fairqueue implementation
					break;

				case "l":
				case "leave":

					event.getMessage().addReaction("U+1F44D").queue();
					event.getGuild().getAudioManager().closeAudioConnection();
					getGuildAudioPlayer(event.getGuild()).scheduler.emptyQueue();
					break;

				case "np":
				case "nowplaying":
					nowPlaying(event.getChannel());
					break;

				case "q":
				case "queue":

					msg = new EmbedBuilder();

					msg.setTitle("Lista de músicas: ");
					msg.setDescription(listTracks(event.getChannel()));

					event.getChannel().sendMessage(msg.build()).queue();
					break;

				case "r":
				case "remove":

					try {

						GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
						AudioTrack trackRemoved = musicManager.scheduler.remove(Integer.parseInt(arguments) - 2);

						msg = new EmbedBuilder();
						msg.setTitle("Removido da fila:");
						msg.setDescription(trackRemoved.getInfo().title);

						event.getChannel().sendMessage(msg.build()).queue();

					} catch (Exception e) {
						e.printStackTrace();
						event.getChannel().sendMessage("Posição nao encontrada").queue();
					}
					break;

				case "summon":
				case "join":

					Member member = event.getMessage().getMember();
					boolean connected = false;
					for (VoiceChannel voiceChannel : event.getGuild().getVoiceChannels()) {
						if (voiceChannel.getMembers().contains(member)) {
							connectToUserVoiceChannel(event.getGuild().getAudioManager(), member, event.getChannel());
							connected = true;
						}
					}

					if (!connected) {
						event.getMessage().getChannel().sendMessage("tu n ta em nenhuma sala corno").queue();
						;
					}
					break;

				case "v":
				case "volume":
					
					event.getGuild().getAudioManager();
					getGuildAudioPlayer(event.getGuild()).player.setVolume(Integer.parseInt(arguments));;
					break;
					
				case "getvolume":
					
					event.getChannel().sendMessage(""+getGuildAudioPlayer(event.getGuild()).player.getVolume()).queue();
					break;
				
				case "pause":
					if (!getGuildAudioPlayer(event.getGuild()).player.isPaused()) {
						getGuildAudioPlayer(event.getGuild()).player.setPaused(true);
					} else {
						event.getChannel().sendMessage("ja ta pausado pora").queue();
					}
					break;
				
				case "resume":
					getGuildAudioPlayer(event.getGuild()).player.setPaused(false);
					break;
				default:
					event.getChannel().sendMessage("q?").queue();
					break;
				}
				
			} else {
				msg = new EmbedBuilder();
				msg.setDescription("entra numa sala primeiro krl");
				event.getChannel().sendMessage(msg.build()).queue();
			}

		}

	}

	private boolean isUserInAChannel(Member member, List<VoiceChannel> voiceChannels) {
		
		for (VoiceChannel voiceChannel : voiceChannels) {
			if (voiceChannel.getMembers().contains(member)) {
				return true;
			}
		}
		return false;
	}

	private void loadAndPlay(final TextChannel channel, String query, final Member member) {

		GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

		String qry = query;
		String trackUrl = query;

		if (query.contains("spotify")) {

			boolean conversionCompleted = false;
			int numberOfTries = 0;

			do {
				try {
					trackUrl = "ytsearch:" + spotifyLinkHandler(query);
					conversionCompleted = true;
				} catch (UnauthorizedException ex) {
					ex.printStackTrace();
					try {
						refreshSpotifyToken();
					} catch (Exception e) {
						e.printStackTrace();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				numberOfTries++;
			} while (!conversionCompleted || numberOfTries < 3);

		} else if (query.contains("youtube")) {
			trackUrl = query;
		} else {
			trackUrl = "ytsearch:" + query;
		}

		playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {

			@Override
			public void trackLoaded(AudioTrack track) {
				play(channel.getGuild(), musicManager, track, member, channel);

				msg = new EmbedBuilder();

				msg.setAuthor("Adicionando na fila: ");
				msg.setTitle(track.getInfo().title, track.getInfo().uri);

				channel.sendMessage(msg.build()).queue();
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				AudioTrack track = playlist.getTracks().get(0);
				play(channel.getGuild(), musicManager, track, member, channel);

				msg = new EmbedBuilder();

				msg.setAuthor("Adicionando na fila: ");
				msg.setTitle(track.getInfo().title, track.getInfo().uri);

				channel.sendMessage(msg.build()).queue();
			}

			@Override
			public void noMatches() {
				// TODO Auto-generated method stub

				msg = new EmbedBuilder();

				msg.setTitle("N achei nada com: " + qry);

				channel.sendMessage(msg.build()).queue();
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				msg = new EmbedBuilder();

				msg.setTitle("Não consegui adicionar esse treco não");

				channel.sendMessage(msg.build()).queue();
			}
		});

	}

	private void refreshSpotifyToken() throws ParseException, SpotifyWebApiException, IOException {

		final AuthorizationCodeCredentials authorizationCodeCredentials = authorizationCodePKCERefreshRequest.execute();

		BufferedWriter br = new BufferedWriter(new FileWriter("C:\\cfg.txt"));
		br.write(authorizationCodeCredentials.getAccessToken());
		br.write("\n");
		br.write(authorizationCodeCredentials.getRefreshToken());
		br.close();
		
		spot.setAccessToken(authorizationCodeCredentials.getAccessToken());
		spot.setRefreshToken(authorizationCodeCredentials.getRefreshToken());
	}

	private String spotifyLinkHandler(String query) throws ParseException, SpotifyWebApiException, IOException {

		List<String> spotifyLink = Arrays.asList(query.split("/"));

		if (spotifyLink.contains("track")) {

			GetTrackRequest trackRequest = spot.getTrack(spotifyLink.get(spotifyLink.size() - 1).split("\\?")[0])
					.build();
//			System.out.println(spotifyLink.get(spotifyLink.size() - 1).split("\\?")[0]);
			Track track = trackRequest.execute();
			return track.getArtists()[0].getName() + " - " + track.getName();
			
		} else if (spotifyLink.contains("playlist")) {

			GetAlbumRequest albumRequest = spot.getAlbum(spotifyLink.get(spotifyLink.size() - 1).split("\\?")[0])
					.build();
			Album album = albumRequest.execute();
			return album.getArtists()[0].getName() + " - " + album.getName() + " full album";
		}
		return "sequencia de vapo";
	}

	private String listTracks(final TextChannel channel) {
		return getGuildAudioPlayer(channel.getGuild()).scheduler.list();
	}

	private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track, Member member, TextChannel channel) {
			connectToUserVoiceChannel(guild.getAudioManager(), member, channel);
			musicManager.scheduler.queue(track);
		
	}

	private void skipTrack(TextChannel channel) {
		GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
		musicManager.scheduler.nextTrack();

		msg = new EmbedBuilder();

		msg.setTitle("Pulando para a próxima musica...");

		channel.sendMessage(msg.build()).queue();
	}

	private void connectToUserVoiceChannel(AudioManager audioManager, Member member, final TextChannel channel) {
		if (!audioManager.isConnected())
//			Consumer<VoiceChannel> l;
//			audioManager.getGuild().getVoiceChannels().forEach();
			for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
				if (voiceChannel.getMembers().contains(member)) {
					audioManager.openAudioConnection(voiceChannel);
				}
			}
	}

	public void nowPlaying(TextChannel channel) {
		AudioTrack track = getGuildAudioPlayer(channel.getGuild()).scheduler.getCurrentTrack();

		if (track != null) {
			AudioTrackInfo trackInfo = track.getInfo();

			msg = new EmbedBuilder();
			msg.setAuthor("Tocando agora: ");
			msg.setTitle(trackInfo.title, trackInfo.uri);

			channel.sendTyping().queue();
			channel.sendMessage(msg.build()).queueAfter(500, TimeUnit.MILLISECONDS);

		} else {
			msg = new EmbedBuilder();
			msg.setTitle("Tem nada tocando agr n krl");

			channel.sendTyping().queue();
			channel.sendMessage(msg.build()).queueAfter(500, TimeUnit.MILLISECONDS);
		}
	}

}
