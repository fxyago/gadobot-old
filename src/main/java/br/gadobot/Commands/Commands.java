package br.gadobot.Commands;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.util.Pair;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

import br.gadobot.Handlers.CommandHandler;
import br.gadobot.Listeners.CommandListener;
import br.gadobot.Player.GadoAudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.Button;

public enum Commands {
	
	ADMIN, ABOUT, PLAY, FORCEPLAY, PAUSE, RESUME, SKIP, JUMP, STOP, SEEK, NOWPLAYING, CLEAR, INFO, QUEUE, SHUFFLE, MOVE, REMOVE, FAIRQUEUE, SUMMON, VOLUME, TESTE, HELP, CHANGE, LEAVE, UNKNOWN;
	
	private static final String THUMBSUP = "U+1F44D";
	
	public static Commands get(String command) {
		
		try {
			return Commands.valueOf(command);
		} catch (Exception e) {
			switch (command) {
			case "P":
				return PLAY;
			case "S":
			case "N":
			case "NEXT":
				return SKIP;
			case "NP":
				return NOWPLAYING;
			case "Q":
			case "LIST":
				return QUEUE;
			case "L":
			case "DESTROY":
				return LEAVE;
			case "FQ":
				return FAIRQUEUE;
			case "JUMPTO":
			case "SKIPTO":
				return JUMP;
			case "JOIN":
				return SUMMON;
			case "VOL":
				return VOLUME;
			default:
				return UNKNOWN;
			}
		}
	}
	
	public static void execute(final GuildMessageReceivedEvent event, final CommandListener listener, AudioPlayerManager playerManager) {
		
		Commands command;
		String arguments;
		
		Pair<String, String> convertedStrings = convertMessage(event.getMessage().getContentRaw());
		
		command = Commands.get(convertedStrings.getFirst());
		arguments = convertedStrings.getSecond();
		
		if (command != UNKNOWN)
			listener.getGuildAudioPlayer(event.getGuild()).scheduler.setChannel(event.getChannel());
		
		switch (command) {
		
		case ADMIN:
			
			break;
			
		case PLAY:
			CommandHandler.play(event, arguments, listener, playerManager);
			break;
		
		case FORCEPLAY:
			
			break;
			
		case CHANGE:
			
			break;
			
		case CLEAR:
			listener.getGuildAudioPlayer(event.getGuild()).scheduler.clearQueue();
			break;
		
		case JUMP:
			addThumbsUp(event);
			listener.getGuildAudioPlayer(event.getGuild()).scheduler.jumpToTrack(Integer.parseInt(arguments));
			break;
			
		case FAIRQUEUE:
			
			break;
			
		case HELP:
			CommandHandler.helpCommand(event.getChannel(), arguments);
			break;
			
		case INFO:
			
			break;
			
		case MOVE: 
			moveTrack(event, listener, arguments);
			break;
		
		case NOWPLAYING: 
			nowPlaying(event, listener);
			break;
		case PAUSE:
			addThumbsUp(event);
			listener.getGuildAudioPlayer(event.getGuild()).player.setPaused(true);
			break;
			
		case QUEUE:
			showQueue(event, listener);
			break;
			
		case REMOVE:
			removeTrack(event, listener, arguments);
			break;
			
		case RESUME:
			addThumbsUp(event);
			listener.getGuildAudioPlayer(event.getGuild()).player.setPaused(false);
			break;
			
		case SEEK:
			seek(event, listener, arguments);
			break;
			
		case SHUFFLE:
			addThumbsUp(event);
			listener.getGuildAudioPlayer(event.getGuild()).scheduler.shuffle();
			break;
			
		case SKIP:
			addThumbsUp(event);
			listener.getGuildAudioPlayer(event.getGuild()).scheduler.nextTrack();
			break;
			
		case STOP:
			addThumbsUp(event);
			stopPlayer(event, listener);
			break;
			
		case SUMMON:
			CommandHandler.connectToUserVoiceChannel(event.getGuild().getAudioManager(), event.getMember());
			break;
			
		case TESTE:
			//TODO: nothing, this is for testing 👀
			break;
			
		case UNKNOWN:
			event.getChannel().sendMessageEmbeds(new EmbedBuilder().setDescription("N tem esse comando ai n").build()).queue();
			break;
			
		case VOLUME:
			addThumbsUp(event);
			volume(event, listener, arguments);
			break;
		case LEAVE:
			addThumbsUp(event);
			clearPlayer(event, listener);
			break;
		case ABOUT:
			event.getChannel().sendMessageEmbeds(new EmbedBuilder().setAuthor("Sobre mim:").setTitle("").setDescription(arguments).build());
			break;
		}
		
	}

	private static void stopPlayer(final GuildMessageReceivedEvent event, final CommandListener listener) {
		listener.getGuildAudioPlayer(event.getGuild()).scheduler.clearQueue();
		listener.getGuildAudioPlayer(event.getGuild()).player.stopTrack();
	}

	private static void clearPlayer(final GuildMessageReceivedEvent event, final CommandListener listener) {
		listener.getGuildAudioPlayer(event.getGuild()).player.destroy();
		listener.getGuildAudioPlayer(event.getGuild()).scheduler.clearQueue();
		event.getGuild().getAudioManager().closeAudioConnection();
	}

	private static void removeTrack(final GuildMessageReceivedEvent event, final CommandListener listener,
			String arguments) {
		String trackRemoved = listener.getGuildAudioPlayer(event.getGuild()).scheduler.removeTrack(Integer.parseInt(arguments));
		event.getChannel().sendMessageEmbeds(new EmbedBuilder()
				.setAuthor("Música: " + trackRemoved)
				.setDescription("Removido da fila")
				.build()).queue();
	}

	private static void moveTrack(final GuildMessageReceivedEvent event, final CommandListener listener,
			String arguments) {
		int indexFrom = Integer.parseInt(arguments.trim().split(",")[0].trim());
		int indexTo = Integer.parseInt(arguments.trim().split(",")[1].trim());
		String trackMoved = listener.getGuildAudioPlayer(event.getGuild()).scheduler.moveTracks(indexFrom, indexTo);
		
		event.getChannel().sendMessageEmbeds(new EmbedBuilder()
				.setAuthor("Música: " + trackMoved)
				.setDescription("Movido de " + indexFrom + " para posição " + indexTo)
				.build()).queue();
	}

	private static void nowPlaying(final GuildMessageReceivedEvent event, final CommandListener listener) {
		GadoAudioTrack gadoTrack = listener.getGuildAudioPlayer(event.getGuild()).scheduler.getCurrentTrack();

		String uri = gadoTrack.getTrack().getInfo().uri;
		Member member = gadoTrack.getMember();

		event.getChannel().sendMessageEmbeds(new EmbedBuilder()
				.setAuthor("Tocando agora: ")
				.setTitle(gadoTrack.getTrack().getInfo().title, uri)
				.setDescription("Pedido por: " + member.getAsMention()).build()).queue();
	}

	private static void showQueue(final GuildMessageReceivedEvent event, final CommandListener listener) {
		String[] lista = listener.getGuildAudioPlayer(event.getGuild()).scheduler.songList();
		if (lista[0].equals("")) {
			event.getChannel().sendMessageEmbeds(new EmbedBuilder()
					.setAuthor("N tem nada tocando n")
					.build()).queue();
		} else {				
			event.getChannel().sendMessageEmbeds(new EmbedBuilder()
					.setAuthor("Lista de músicas: ")
					.setDescription(lista[0])
					.setFooter("Pagina 1/" + lista.length)
					.build()).setActionRow(
							Button.danger("firstPage", Emoji.fromMarkdown("<:first:901482748957585478>")),
							Button.danger("prevPage", Emoji.fromMarkdown("<:prev:901482652991909928>")),
							Button.danger("nextPage", Emoji.fromMarkdown("<:next:901482602064670741>")),
							Button.danger("lastPage", Emoji.fromMarkdown("<:last:901482705311637574>")))
					.queue();
		}
	}

	private static void volume(final GuildMessageReceivedEvent event, final CommandListener listener,
			String arguments) {
		if (arguments.equals("")) {
			event.getChannel().sendMessageEmbeds(new EmbedBuilder()
					.setAuthor("Volume atual: ")
					.setTitle(listener.getGuildAudioPlayer(event.getGuild()).player.getVolume() + "")
					.build())
					.queue();
		} else {
			try {
				listener.getGuildAudioPlayer(event.getGuild()).player.setVolume(Integer.parseInt(arguments));					
				event.getChannel().sendMessageEmbeds(new EmbedBuilder()
						.setAuthor("Mudando volume para: ")
						.setTitle(arguments)
						.build())
				.queue();
			} catch (NumberFormatException e) {
				event.getChannel().sendMessageEmbeds(new EmbedBuilder()
						.setDescription("Essa porra ai n é número krl 😡")
						.build())
				.queue();
			}
			
		}
	}

	private static void addThumbsUp(final GuildMessageReceivedEvent event) {
		event.getMessage().addReaction(THUMBSUP).queue();
	}

	private static void seek(final GuildMessageReceivedEvent event, final CommandListener listener, String arguments) {
		int totalTime;
		if (arguments.contains(":")) {
			totalTime = Integer.parseInt(arguments.split(":")[0]) * 60
					+ Integer.parseInt(arguments.split(":")[1]);
		} else {
			totalTime = Integer.parseInt(arguments);
		}
		
		AudioPlayer player = listener.getGuildAudioPlayer(event.getGuild()).player;
		int trackDuration = (int) Math.floor(player.getPlayingTrack().getDuration()/1000d);
		
		if (trackDuration > totalTime && totalTime >= 0) {
			addThumbsUp(event);
			listener.getGuildAudioPlayer(event.getGuild()).scheduler.seek(totalTime * 1000);
			
		} else if (totalTime < 0) {
			event.getChannel().sendMessageEmbeds(new EmbedBuilder()
					.setTitle("O tempo não pode ser inferior a 0").build()).queue();
			
		} else if (totalTime >= trackDuration) {
			event.getChannel().sendMessageEmbeds(new EmbedBuilder()
					.setTitle("O tempo " + totalTime + " s, é maior ou igual à duração da música: " + trackDuration/1000d + " s")
					.build()).queue();
		}
	}
	
	private static Pair<String, String> convertMessage(String message) {
		
		List<String> messageParsed = Arrays.asList(message.split(" "));
		String command = messageParsed.get(0).substring(1).toUpperCase();
		String arguments = "";
		
		for (int i = 1; i < messageParsed.size(); i++) {
			arguments += messageParsed.get(i);
			arguments += " ";
		}
		arguments = arguments.trim();
		
		return new Pair<String, String>(command, arguments);
		
	}
	
}
