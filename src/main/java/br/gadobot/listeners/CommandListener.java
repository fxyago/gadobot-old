package br.gadobot.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;

import br.gadobot.InitApp;
import br.gadobot.commands.Commands;
import br.gadobot.player.GuildMusicManager;
import br.gadobot.player.TrackScheduler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class CommandListener extends ListenerAdapter {

	private final AudioPlayerManager playerManager;
	private final Map<Long, GuildMusicManager> musicManagers;
	private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);
	private ScheduledFuture<?> idleTimer;
	
	public CommandListener() {
		this.musicManagers = new HashMap<>();
		this.playerManager = new DefaultAudioPlayerManager();
		AudioSourceManagers.registerRemoteSources(playerManager);
		AudioSourceManagers.registerLocalSource(playerManager);
	}
	
	public synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
		long guildId = Long.parseLong(guild.getId());
		GuildMusicManager musicManager = musicManagers.get(guildId);

		if (musicManager == null) {
			musicManager = new GuildMusicManager(playerManager);
			musicManager.scheduler.setGuild(guild);
			musicManager.scheduler.setListener(this);
			musicManager.scheduler.setManager(playerManager);
			musicManagers.put(guildId, musicManager);
		}
		
		guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
		
		return musicManager;
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		String message = event.getMessage().getContentRaw();
		if (message.startsWith(InitApp.PREFIX))
			Commands.execute(event, this, playerManager);
	}
	
	@Override
	public void onGuildVoiceJoin(GuildVoiceJoinEvent event) {
		
		TrackScheduler scheduler = getGuildAudioPlayer(event.getGuild()).scheduler;
		
		if (scheduler.isConnected() && event.getChannelJoined().equals(scheduler.getVoiceChannel())) {
			if (idleTimer != null) {
				idleTimer.cancel(true);
			}
		}
	}
	
	@Override
	public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
		
		TrackScheduler scheduler = getGuildAudioPlayer(event.getGuild()).scheduler;
		
		if (scheduler.isConnected()) {
			if (event.getMember().getIdLong() == InitApp.SELF_ID) {
				if (idleTimer != null) {
					idleTimer.cancel(true);
				}
			} else if (scheduler.getVoiceChannel().equals(event.getChannelLeft())
					&& event.getChannelLeft().getMembers().size() == 1) {
				idleTimer = executorService.schedule(() -> {
					scheduler.leaveChannelOnIdle();
					scheduler.clearQueue();
				}, 5, TimeUnit.MINUTES);
			} 
		}
	}
	
	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		
		String id = "";
		Message message;
		String pages;
		int maxPages;
		int currentPage;
		
		id = event.getButton().getId();

		switch (id) {
		case "nextPage":
			
			message = event.getMessage();
			
			pages = extractPages(message);
			
			maxPages = Integer.parseInt(pages.split("/")[1]);
			currentPage = Integer.parseInt(pages.split("/")[0]);
			
			if (currentPage < maxPages && getGuildAudioPlayer(event.getGuild()).scheduler.getCurrentTrack() != null) {
				
				String[] queue = getGuildAudioPlayer(event.getGuild()).scheduler.songList();
				String list = queue[currentPage];
				maxPages = queue.length;
				
				event.editMessageEmbeds(new EmbedBuilder()
						.setTitle("Lista de m�sicas: ")
						.setDescription(list.trim())
						.setFooter("Pagina " + (currentPage+1) + "/" + maxPages)
						.build()).queue();
				
			} else {
				event.deferEdit().queue();				
			}
			
			break;

		case "prevPage":

			message = event.getMessage();
			
			pages = extractPages(message);
			
			maxPages = Integer.parseInt(pages.split("/")[1]);
			currentPage = Integer.parseInt(pages.split("/")[0]);
			
			if (getGuildAudioPlayer(event.getGuild()).scheduler.getCurrentTrack() != null) {
				
				String[] queue = getGuildAudioPlayer(event.getGuild()).scheduler.songList();
				String list = queue[currentPage > 1 ? currentPage-2 : 0];
				maxPages = queue.length;
				currentPage = currentPage > 1 ? currentPage - 1 : currentPage;
				
				event.editMessageEmbeds(new EmbedBuilder()
						.setTitle("Lista de m�sicas: ")
						.setDescription(list.trim())
						.setFooter("Pagina " + (currentPage) + "/" + maxPages)
						.build()).queue();
				
			} else {
				event.deferEdit().queue();				
			}
			
			break;
			
		case "firstPage":
			
			message = event.getMessage();
			
			pages = extractPages(message);
			
			maxPages = Integer.parseInt(pages.split("/")[1]);
			currentPage = Integer.parseInt(pages.split("/")[0]);
			
			if (currentPage >= 1 && getGuildAudioPlayer(event.getGuild()).scheduler.getCurrentTrack() != null) {
				
				String[] queue = getGuildAudioPlayer(event.getGuild()).scheduler.songList();
				String list = queue[0];
				maxPages = queue.length;
				
				event.editMessageEmbeds(new EmbedBuilder()
						.setTitle("Lista de m�sicas: ")
						.setDescription(list.trim())
						.setFooter("Pagina 1/" + maxPages)
						.build()).queue();
			} else {
				event.deferEdit().queue();
			}
			
			break;

		case "lastPage":

			message = event.getMessage();
			
			pages = extractPages(message);
			
			maxPages = Integer.parseInt(pages.split("/")[1]);
			currentPage = Integer.parseInt(pages.split("/")[0]);
			
			if (currentPage < maxPages && getGuildAudioPlayer(event.getGuild()).scheduler.getCurrentTrack() != null) {
				
				String[] queue = getGuildAudioPlayer(event.getGuild()).scheduler.songList();
				maxPages = queue.length;
				String list = queue[maxPages-1];
				
				event.editMessageEmbeds(new EmbedBuilder()
						.setTitle("Lista de m�sicas: ")
						.setDescription(list.trim())
						.setFooter("Pagina " + maxPages + "/" + maxPages)
						.build()).queue();
			} else {				
				event.deferEdit().queue();
			}
			
			break;

		case "gay":
			event.deferEdit().queue();
			event.getUser().openPrivateChannel().flatMap(channel -> channel.sendMessage("vc perdeu O JOGO")).queue();
			break;

		default:
			event.deferEdit().queue();
			break;
		}
		
	}
	
	private String extractPages(Message message) {
		String pages = message.getEmbeds().get(0)
				.getFooter().getText()
				.substring(6, message.getEmbeds().get(0)
				.getFooter().getText().length()).trim();
		return pages;
	}
	
	
}
