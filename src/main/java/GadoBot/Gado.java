package GadoBot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.security.auth.login.LoginException;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

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

	static final String spotifyToken = "";

	public static String prefix = ";";
	
	private static EmbedBuilder msg;
	
	public static void main(String[] args) throws LoginException, InterruptedException {

		JDABuilder.create("ODkxMjkzNjQzMjMzNjk3ODMz.YU8P4w.wHgu8gfx08bw858RmdgJcx6RB3I", 
				GatewayIntent.GUILD_MESSAGES,
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
			
			List<String> messageParsed = Arrays.asList(event.getMessage().getContentRaw().toLowerCase().split(" "));
						
			String command = messageParsed.get(0).substring(1);
			String arguments = "";
			for (int i = 1; i < messageParsed.size(); i++) {
				arguments += messageParsed.get(i);
				arguments += " ";
			}
			arguments = arguments.trim();
			
			switch (command) {
			
			case "p":
			case "play":
				loadAndPlay(event.getChannel(), arguments, event.getMessage().getMember());
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
				
//				event.getChannel().sendTyping().queue();
//				event.getChannel().sendMessage("Adeus cornos").queueAfter(500, TimeUnit.MILLISECONDS);
				
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
				
				arguments = arguments.trim();
				try {
					
					GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
					AudioTrack trackRemoved = musicManager.scheduler.remove(Integer.parseInt(arguments)-2);
					
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
					event.getMessage().getChannel().sendMessage("tu n ta em nenhuma sala corno").queue();;
				}
				
			default:
				event.getChannel().sendMessage("q?").queue();
				break;
			}
			
		}

	}
	
	private void loadAndPlay(final TextChannel channel, String query, final Member member) {
				
		GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
		
		String qry = query;
		String trackUrl = query;
		
		if (!query.contains("youtube")) {
			trackUrl = "ytsearch:" + query;
		} else {
			trackUrl = query;
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
	
	private String listTracks(final TextChannel channel) {
		return getGuildAudioPlayer(channel.getGuild()).scheduler.list();
	}
	
	private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track, Member member, TextChannel channel) {
		
		if (member.getVoiceState().getChannel() != null) {
			connectToUserVoiceChannel(guild.getAudioManager(), member, channel);
			musicManager.scheduler.queue(track);
		} else {
			channel.sendMessage("entra numa sala primeiro krl").queue();
		}
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
		channel.sendTyping().queue();
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
