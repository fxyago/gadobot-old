package br.gadobot.Player;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import br.gadobot.Handlers.CommandHandler;
import br.gadobot.Listeners.CommandListener;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.TextChannel;

public class TrackScheduler extends AudioEventAdapter {

	private CommandListener listener;
	private Guild guild;
	private AudioPlayerManager playerManager;
	private final AudioPlayer player;
	private final LinkedBlockingDeque<GadoAudioTrack> queue;
	private GadoAudioTrack currentTrack;
	private TextChannel currentChannel;
	private Timer timer = new Timer();
	
	public TrackScheduler(AudioPlayer player) {
		
		this.player = player;
		this.queue = new LinkedBlockingDeque<>();
		this.currentTrack = new GadoAudioTrack();
	}
	
	public void queue(GadoAudioTrack gadoTrack) {
		
		if (queue.isEmpty() && gadoTrack.getTrack() == null)
			gadoTrack.setTrack(CommandHandler.queryTrack(playerManager, gadoTrack.getSongName(), listener.getGuildAudioPlayer(guild)));
		
		if (player.startTrack(gadoTrack.getTrack(), true)) {
			currentTrack = gadoTrack;
			refreshNowPlaying();
			timer.cancel();
		} else {
			queue.offer(gadoTrack);
		}
		
	}

	public String[] songList() {
		
		Double pages = Math.ceil(queue.size()/10d);
		String[] songList = new String[pages.intValue()];
		BlockingQueue<GadoAudioTrack> tracks = new LinkedBlockingQueue<>();
		tracks.addAll(queue);
		String temp;
		int songIndex = 1;
		for (int i = 0; i < pages; i++) {
			temp = "";
			for (int j = 0; j < 10; j++) {
				temp += tracks.peek() == null ? "" : songIndex + " - " + tracks.poll().getSongName() + "\n";
				songIndex++;
			}
			songList[i] = temp.trim();
		}
		return songList;
	}
	
	public GadoAudioTrack getCurrentTrack() {
		return this.currentTrack;
	}
	
	public void playNow(GadoAudioTrack gadoTrack) {
		currentTrack = gadoTrack;
		player.startTrack(gadoTrack.getTrack(), false);
	}
	
	public void clearQueue() {
		queue.clear();
		currentTrack = new GadoAudioTrack();
	}
	
	public void nextTrack() {
		currentTrack = queue.poll();
		
		if (!currentTrack.equals(null)) {
			if (currentTrack.getTrack() == null)
				currentTrack.setTrack(CommandHandler.queryTrack(playerManager, 
						currentTrack.getSongName(),
						listener.getGuildAudioPlayer(guild)));
			
			player.startTrack(currentTrack.getTrack(), false);
			refreshNowPlaying();
		} else {
			currentTrack = new GadoAudioTrack();
		}
	}
	
	private void refreshNowPlaying() {
		MessageHistory history = currentChannel.getHistory();
		history.retrievePast(3).submit();
		history.retrievePast(3).complete();
		boolean isFound = false;
		
		List<Message> messages = history.getRetrievedHistory();
		for (Message message : messages) {
			if (message.getContentRaw().equals("")) {
				MessageEmbed embed = message.getEmbeds().get(0);
				if (embed.getDescription() != null && embed.getDescription().contains("Pedido por:")) {
					message.editMessageEmbeds(new EmbedBuilder()
							.setAuthor("Tocando agora:")
							.setTitle(currentTrack.getTrack().getInfo().title, currentTrack.getTrack().getInfo().uri)
							.setDescription("Pedido por: " + currentTrack.getMember().getAsMention())
							.build()).queue();
					isFound = true;
				}
			}
		}
		if (!isFound) {
			currentChannel.sendMessageEmbeds(new EmbedBuilder()
					.setAuthor("Tocando agora:")
					.setTitle(currentTrack.getTrack().getInfo().title, currentTrack.getTrack().getInfo().uri)
					.setDescription("Pedido por: " + currentTrack.getMember().getAsMention())
					.build()).queue();
		}
	}

	@Override
	public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
		
		if (endReason.mayStartNext) {
			if (queue.isEmpty()) {
				currentTrack = new GadoAudioTrack();

				timer.schedule(new TimerTask() {
					
					@Override
					public void run() {
						guild.getAudioManager().closeAudioConnection();
						currentChannel.sendMessageEmbeds(new EmbedBuilder()
								.setDescription("Cabou a música, to indo nessa rapazeada 😎")
								.build()).queue();
					}
					
				}, 2*60*1000);
				
			} else
				nextTrack();
		}
		
	}
	
	public void shuffle() {
		List<GadoAudioTrack> tempQueue = new LinkedList<>();
		queue.drainTo(tempQueue);
		Collections.shuffle(tempQueue);
		queue.addAll(tempQueue);
	}
	
	@Override
	public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
		System.out.println("An exception occurred while trying to play the track, attempting to recover...");
		AudioTrack cloneTrack = track.makeClone();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {}
		System.out.println("Trying to replay the track...");
		boolean isPlayed = player.startTrack(cloneTrack, false);
		System.out.println("Recovered from exception: " + isPlayed);
	}

	public void setGuild(Guild guild) {
		this.guild = guild;
	}
	
	public void setListener(CommandListener listener) {
		this.listener = listener;
	}

	public void setManager(AudioPlayerManager playerManager) {
		this.playerManager = playerManager;
	}

	public void seek(int duration) {
		AudioTrack track = player.getPlayingTrack().makeClone();
		track.setPosition(duration);
		player.playTrack(track);
	}

	public String moveTracks(int indexFrom, int indexTo) {
		LinkedList<GadoAudioTrack> trackTemp = new LinkedList<>();
		queue.drainTo(trackTemp);
		
		GadoAudioTrack trackMoved = trackTemp.get(indexFrom-1);
		trackTemp.remove(indexFrom-1);
		trackTemp.add(indexTo-1, trackMoved);
		
		queue.addAll(trackTemp);
		
		return trackMoved.getSongName();
	}

	public String removeTrack(int trackIndex) {
		LinkedList<GadoAudioTrack> tempQueue = new LinkedList<>();
		queue.drainTo(tempQueue);
		
		String trackRemoved = tempQueue.get(trackIndex-1).getSongName();
		tempQueue.remove(trackIndex-1);
		queue.addAll(tempQueue);
		
		return trackRemoved;
	}
	
	public void setChannel(TextChannel channel) {
		this.currentChannel = channel;
	}

	public void jumpToTrack(int trackIndex) {
		for (int i = 0; i < trackIndex-1; i++) queue.pop();
		nextTrack();
	}
}
