package br.gadobot.Handlers;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import br.gadobot.Gado;
import br.gadobot.Commands.Commands;
import br.gadobot.Listeners.CommandListener;
import br.gadobot.Player.GadoAudioTrack;
import br.gadobot.Player.GuildMusicManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.managers.AudioManager;

public class PlayCommandHandler {
	
	private enum ResultType {
		YOUTUBE_TRACK, SPOTIFY_TRACK, YOUTUBE_PLAYLIST, SPOTIFY_PLAYLIST, YOUTUBE_SEARCH
	}
		
	public static void play(final GuildMessageReceivedEvent event, final String arguments, final CommandListener listener, final AudioPlayerManager playerManager) {
		
		boolean isSearch = event.getMessage().getContentRaw().startsWith(Gado.PREFIX + "search");

		if (arguments.contains("spotify.com")) {
			if (arguments.contains("playlist")) {
				queuePlaylist(event, arguments, listener);
			} else if (arguments.contains("album")) {
				queueAlbum(event, arguments, listener);
			} else {
				String spotifyQuery = "";
				try {
					spotifyQuery = Gado.spotifyHandler.trackConverterAsync(arguments).get();
				} catch (Exception e) {
					e.printStackTrace();
				}
				loadAndPlay(event, "ytsearch:" + spotifyQuery, listener, playerManager, isSearch ? ResultType.YOUTUBE_SEARCH : ResultType.SPOTIFY_TRACK);				
			}
			
		} else if (arguments.contains("youtube.com")) {
			if (arguments.contains("list")) {
				loadAndPlay(event, arguments, listener, playerManager, isSearch ? ResultType.YOUTUBE_SEARCH : ResultType.YOUTUBE_PLAYLIST);
			} else {
				loadAndPlay(event, arguments, listener, playerManager, isSearch ? ResultType.YOUTUBE_SEARCH : ResultType.YOUTUBE_TRACK);
			}
		} else { 
			loadAndPlay(event, "ytsearch:" + arguments, listener, playerManager, ResultType.YOUTUBE_TRACK);
		}
		
	}

	private static void queueAlbum(final GuildMessageReceivedEvent event, final String arguments, final CommandListener listener) {
		
		Future<List<String>> spotifyQueries = Gado.spotifyHandler.albumConverterAsync(arguments);
		
		event.getChannel().sendMessageEmbeds(new EmbedBuilder()
				.setAuthor("Adicionando à fila: ").setTitle(Gado.spotifyHandler.getNumberOfTracksAlbum(arguments) + " músicas")
				.build()).queue();
		
		try {
			queueRemaining(event, listener, spotifyQueries);
			connectToUserVoiceChannel(event.getGuild().getAudioManager(), event.getMember());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void queuePlaylist(final GuildMessageReceivedEvent event, final String arguments, final CommandListener listener) {
		
		Future<List<String>> spotifyQueries = Gado.spotifyHandler.playlistConverterAsync(arguments);
		int playlistSize = Gado.spotifyHandler.getNumberOfTracks(arguments);
		
		event.getChannel().sendMessageEmbeds(new EmbedBuilder()
				.setAuthor("Adicionando à fila: ").setTitle(playlistSize + " músicas")
				.build()).queue();
		
		connectToUserVoiceChannel(event.getGuild().getAudioManager(), event.getMember());
		
		queueFirstTrack(event, arguments, listener);
		new Thread(() -> {
			queueRemaining(event, listener, spotifyQueries);			
		}).start();
	}

	private static void queueRemaining(final GuildMessageReceivedEvent event, final CommandListener listener, Future<List<String>> spotifyQueries) {
		try {
			spotifyQueries.get().forEach(q -> queueSilently(listener.getGuildAudioPlayer(event.getGuild()), event.getMember(), q));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void queueFirstTrack(final GuildMessageReceivedEvent event, final String arguments, final CommandListener listener) {
		try {
			queueSilently(listener.getGuildAudioPlayer(event.getGuild()), event.getMember(), Gado.spotifyHandler.firstTrackConverterAsync(arguments).get());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void loadAndPlay(final GuildMessageReceivedEvent event, final String query, final CommandListener listener, final AudioPlayerManager playerManager, ResultType t) {
		
		GuildMusicManager musicManager = listener.getGuildAudioPlayer(event.getGuild());
		playerManager.loadItemOrdered(musicManager, query, new AudioLoadResultHandler() {
			
			@Override
			public void trackLoaded(AudioTrack track) {
				event.getChannel().sendMessage("Adicionando à fila: " + track.getInfo().title).queue();
				queueTrack(event.getGuild().getAudioManager(), new GadoAudioTrack(track, event.getMember(), track.getInfo().title), musicManager);
			}
			
			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				
				AudioTrack track;
				
				switch (t) {
				case SPOTIFY_TRACK:
					track = playlist.getTracks().get(0);
					queueTrack(event.getGuild().getAudioManager(),
							new GadoAudioTrack(track, event.getMember(), track.getInfo().title),
							musicManager);
					event.getChannel().sendMessageEmbeds(new EmbedBuilder()
							.setAuthor("Adicionando à fila: ").setTitle(track.getInfo().title, track.getInfo().uri)
							.build()).queue();
					break;

				case SPOTIFY_PLAYLIST:
					track = playlist.getTracks().get(0);
					
					queueTrack(event.getGuild().getAudioManager(),
							new GadoAudioTrack(track, event.getMember(), track.getInfo().title),
							musicManager);
					break;
					
				case YOUTUBE_TRACK:
					
					for (int i = 0; i < playlist.getTracks().size(); i++) {
						String regex = ".*?[Ll][Ii][Vv][Ee].*?";
						if (playlist.getTracks().get(i).getInfo().title.matches(regex)) {
							continue;
						} else {
							track = playlist.getTracks().get(i);
							queueTrack(event.getGuild().getAudioManager(),
									new GadoAudioTrack(track, event.getMember(), track.getInfo().title),
									musicManager);
							event.getChannel().sendMessageEmbeds(new EmbedBuilder()
									.setAuthor("Adicionando à fila: ").setTitle(track.getInfo().title, track.getInfo().uri)
									.build()).queue();
							break;
						}
					}
					break;
					
				case YOUTUBE_PLAYLIST:
					playlist.getTracks().forEach(t -> queueTrack(event.getGuild().getAudioManager(), new GadoAudioTrack(t, event.getMember(), t.getInfo().title), musicManager));
					event.getChannel().sendMessageEmbeds(new EmbedBuilder()
							.setAuthor("Adicionando à fila " + playlist.getTracks().size() + " músicas da playlist:")
							.setTitle(playlist.getName(), query)
							.build())
						.queue();
					break;
				case YOUTUBE_SEARCH:
					break;
				}
			}
			
			@Override
			public void noMatches() {
				System.out.println(query);
				event.getChannel().sendMessage("Não achei nada").queue();
			}
			
			@Override
			public void loadFailed(FriendlyException exception) {
				event.getChannel().sendMessage("O load falhou :/").queue();
				exception.printStackTrace();
			}
		});
	}
	
	private static void queueTrack(AudioManager audioManager, GadoAudioTrack audioTrack, GuildMusicManager musicManager) {
		connectToUserVoiceChannel(audioManager, audioTrack.getMember());
		musicManager.scheduler.queue(audioTrack);
	}
	
	public static void connectToUserVoiceChannel(AudioManager audioManager, Member member) {
		if (!audioManager.isConnected())
			for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
				if (voiceChannel.getMembers().contains(member)) {
					audioManager.setSelfDeafened(true);
					audioManager.openAudioConnection(voiceChannel);
				}
			}
	}
	
	public static void queueSilently(GuildMusicManager musicManager, Member member, String query) {
		musicManager.scheduler.queue(new GadoAudioTrack(member, query));
	}
	
//	@SuppressWarnings("static-access")
	public static AudioTrack queryTrack(AudioPlayerManager playerManager, String query, GuildMusicManager musicManager) {
		
		LinkedList<AudioTrack> trackReceived = new LinkedList<>();
				
		new Thread(() -> {

			playerManager.loadItemOrdered(musicManager, "ytsearch:" + query, new AudioLoadResultHandler() {

				@Override
				public void trackLoaded(AudioTrack track) {
					trackReceived.add(track);
				}

				@Override
				public void playlistLoaded(AudioPlaylist playlist) {
					trackReceived.add(playlist.getTracks().get(0));
				}

				@Override
				public void noMatches() {
					System.out.println("No matches?");
					trackReceived.add(null);
				}

				@Override
				public void loadFailed(FriendlyException exception) {
					exception.printStackTrace();
					System.out.println("Load failed");
					trackReceived.add(null);
				}
			});
		
		}).start();
		
		new Thread(() -> {
			while (trackReceived.size() == 0) {
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).run();
		
		return trackReceived.get(0);
	}
	
	public static void helpCommand(final TextChannel channel, String command) {

		StringBuffer sb = new StringBuffer();
		
		if (command.equals("")) {
			sb.append("`Música:`\n");
			sb.append("play, pause, resume, stop, skip, seek, forceplay, volume\n");
			sb.append("`Fila:`\n");
			sb.append("queue, clear, shuffle, move, remove, fairqueue, change\n");
			sb.append("`Informações:`\n");
			sb.append("nowplaying, info\n");
			sb.append("`Sobre o bot:`\n");
			sb.append("about");
			channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Lista de comandos:")
					.setDescription(sb.toString())
					.build()).queue();
		} else {

			String commandWithPrefix = Gado.PREFIX + Commands.get(command.toUpperCase()).toString().toLowerCase();

			switch (Commands.get(command.toUpperCase())) {
			case ADMIN:
				break;
			case CHANGE:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: change")
						.setDescription("`" + commandWithPrefix + "` -> sim").build()).queue();
				break;
			case CLEAR:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: clear")
						.setDescription("`" + commandWithPrefix + "` -> limpa a fila e para de tocar música").build())
						.queue();
				break;
			case FAIRQUEUE:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: fairqueue").setDescription("Alias: `fq`"
						+ "`" + commandWithPrefix + "` -> liga o fairqueue"
						+ "\ni.e. músicas são ordenadas de modo a tocar uma música pedida de cada usuário por vez")
						.build()).queue();
				break;
			case HELP:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: clear")
						.setDescription("`" + commandWithPrefix + "` -> limpa a fila e para de tocar música").build())
						.queue();
				break;
			case INFO:
				channel.sendMessageEmbeds(
						new EmbedBuilder().setTitle("Comando: info")
								.setDescription("`" + commandWithPrefix
										+ " [index]` -> mostra informação sobre uma música da fila"
										+ "\nConsulte os index das músicas pelo comando `queue`")
								.build())
						.queue();
				break;
			case MOVE:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: move")
						.setDescription("`" + commandWithPrefix
								+ " [indexDe], [indexPara]` -> move uma música para outro lugar da fila"
								+ "\nConsulte os index das músicas pelo comando `queue`" + "\nEx.: `"
								+ commandWithPrefix + " 39, 3` -> move a música de número 39, para a posição 3")
						.build()).queue();
				break;
			case NOWPLAYING:
				channel.sendMessageEmbeds(
						new EmbedBuilder()
								.setTitle("Comando: nowplaying").setDescription("Alias: `np`\n" + "`"
								+ commandWithPrefix + "` -> mostra informação sobre a música atual \n`")
						.build()).queue();
				break;
			case PAUSE:
				channel.sendMessageEmbeds(
						new EmbedBuilder().setTitle("Comando: pause").setDescription("`" + commandWithPrefix
								+ "` -> pausa o bot" + "\n Use o comando `resume` para continuar a música")
						.build()).queue();
				break;
			case JUMP:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: jump")
						.setDescription("Alias: `jumpto`, `skipto`\n" + "`" + commandWithPrefix
								+ " [index]` -> pula para a música contida no index da fila"
								+ "\nTodas as músicas anteriores a ela são descartadas")
						.build()).queue();
				break;
			case PLAY:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: play").setDescription("Alias: `p`"
						+ "\n`" + commandWithPrefix + " [musica]`-> pede uma música para o bot"
						+ "\n[musica] pode ser um link do youtube, spotify ou um texto (texto a ser pesquisado no youtube)"
						+ "\nTambém suporta link de playlists").build()).queue();
				break;
			case FORCEPLAY:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: forceplay")
						.setDescription("Alias: `fplay`" + "\n`" + commandWithPrefix
								+ "[música]` -> troca a música que estiver tocando por outra")
						.build()).queue();
				break;
			case QUEUE:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: queue")
						.setDescription(
								"Alias: `q` `list`" + "\n`" + commandWithPrefix + "` -> mostra a fila de músicas")
						.build()).queue();
				break;
			case REMOVE:
				channel.sendMessageEmbeds(
						new EmbedBuilder().setTitle("Comando: remove")
								.setDescription("`" + commandWithPrefix
										+ " [index]` -> remove da fila a música do index referido"
										+ "\nConsulte os index das músicas pelo comando `queue`")
								.build())
						.queue();
				break;
			case RESUME:
				channel.sendMessageEmbeds(
						new EmbedBuilder().setTitle("Comando: resume")
								.setDescription("`" + commandWithPrefix
										+ "` -> continua a música caso o bot esteja pausado/parado"
										+ "\n\nNada acontece caso o bot não esteja pausado")
								.build())
						.queue();
				break;
			case SEEK:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: seek")
						.setDescription("`" + commandWithPrefix
								+ " [tempo]` -> muda o tempo da música para o tempo referido"
								+ "\n\n`[tempo]` pode ser em segundos `120`, ou em minutos `2:00`" + "\n\nEx.: `"
								+ commandWithPrefix + " 3:10` -> pula a música atual para 3 minutos e 10 segundos"
								+ "\n\nTempo referido obviamente não pode ser maior que o tempo máximo da música")
						.build()).queue();
				break;
			case SHUFFLE:
				channel.sendMessageEmbeds(
						new EmbedBuilder()
								.setTitle("Comando: shuffle").setDescription("`" + commandWithPrefix
										+ "` -> embaralha a fila" + "\ni.e. liga o aleatório, não pode ser desfeito")
								.build())
						.queue();
				break;
			case SKIP:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: skip")
						.setDescription("Alias: `next` `n` `s`\n" + "`" + commandWithPrefix
								+ "` -> pula para a próxima música da fila"
								+ "\n(ou para de tocar caso não haja nenhuma)")
						.build()).queue();
				break;
			case STOP:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: stop")
						.setDescription("`" + commandWithPrefix + "` -> pausa o bot e para de tocar a música atual")
						.build()).queue();
				break;
			case SUMMON:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: summon")
						.setDescription(
								"Alias: `join`\n" + "`" + commandWithPrefix + "` -> chama o bot para a sua sala")
						.build()).queue();
				break;
			case TESTE:
				break;

			case UNKNOWN:
				channel.sendMessageEmbeds(
						new EmbedBuilder().setDescription("Acho q n tenho esse comando ai n man").build()).queue();
				break;

			case VOLUME:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: volume")
						.setDescription("Alias: `vol`\n" + "`" + commandWithPrefix + "` -> mostra o volume atual"
						+ "\n`" + commandWithPrefix + " 100` -> muda o volume para 100")
						.build()).queue();
				break;
			case LEAVE:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: clear")
						.setDescription("Alias: `l` `destroy`\n" + "`"
						+ commandWithPrefix + "` -> expulsa o bot da sala (ele chora no banho dps)")
						.build()).queue();
				break;
			case LYRICS:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: lyrics")
						.setDescription("Alias: `ly`\n" + "`"
						+ commandWithPrefix + "` -> mostra a letra da musica atual")
						.build()).queue();
				break;
			case ABOUT:
				channel.sendMessageEmbeds(new EmbedBuilder().setTitle("Comando: about")
						.setDescription("`" + commandWithPrefix + "` -> mostra informações sobre o bot")
						.build()).queue();
				break;
			}
		}
	}

}
