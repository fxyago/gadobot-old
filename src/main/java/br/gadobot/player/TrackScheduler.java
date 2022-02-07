package br.gadobot.player;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import br.gadobot.handlers.PlayCommandHandler;
import br.gadobot.listeners.CommandListener;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;

public class TrackScheduler extends AudioEventAdapter {

	private CommandListener listener;
	private Guild guild;
	private AudioPlayerManager playerManager;
	private VoiceChannel voiceChannel;
	private final AudioPlayer player;
	private final LinkedBlockingDeque<GadoAudioTrack> queue;
	private GadoAudioTrack currentTrack, lastTrack;
	private TextChannel currentChannel;
	private Timer timer = new Timer();
	private int nOfTries;
	private boolean connected;
	
	public TrackScheduler(AudioPlayer player) {
		
		this.player = player;
		this.queue = new LinkedBlockingDeque<>();
		this.currentTrack = new GadoAudioTrack();
	}
	
	public synchronized void queue(GadoAudioTrack gadoTrack) {
		
		if (queue.isEmpty() && gadoTrack.getTrack() == null)
			gadoTrack.setTrack(PlayCommandHandler.queryTrack(playerManager, gadoTrack.getSongName(), listener.getGuildAudioPlayer(guild)));
		
		if (player.startTrack(gadoTrack.getTrack(), true)) {
			currentTrack = gadoTrack;
			refreshNowPlaying();
			this.timer.cancel();
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
		if (queue.peek() != null) {
			lastTrack = currentTrack;
			currentTrack = queue.poll();
			if (currentTrack.getTrack() == null)
				currentTrack.setTrack(PlayCommandHandler.queryTrack(playerManager, 
						currentTrack.getSongName(),
						listener.getGuildAudioPlayer(guild)));
			
			player.startTrack(currentTrack.getTrack(), false);
			refreshNowPlaying();
		} else {
			currentTrack = new GadoAudioTrack();
			lastTrack = new GadoAudioTrack();
			player.stopTrack();
		}
		nOfTries = 0;
	}
	
	public void previousTrack() {
		if (lastTrack.getMember() != null) {			
			queue.addFirst(currentTrack);
			player.startTrack(lastTrack.getTrack(), false);
			currentTrack = lastTrack;
			lastTrack = new GadoAudioTrack();
		} else {
			currentChannel.sendMessageEmbeds(new EmbedBuilder()
					.setDescription("Po man, eu so guardo 1 musica anterior por vez, acalma o cu ai")
					.build()).queue();
		}
	}
	
	private void refreshNowPlaying() {
		MessageHistory history = currentChannel.getHistory();
		history.retrievePast(3).submit();
		history.retrievePast(3).complete();
		boolean isFound = false;
		
		List<Message> messages = history.getRetrievedHistory();
		for (Message message : messages) {
			if (message.getContentRaw().equals("") && message.getEmbeds().size() > 0) {
				MessageEmbed embed = message.getEmbeds().get(0);
				if (embed.getDescription() != null && embed.getDescription().contains("Pedido por:")) {
					message.editMessageEmbeds(new EmbedBuilder()
							.setAuthor("Tocando agora:")
							.setTitle(currentTrack.getTrack().getInfo().title, currentTrack.getTrack().getInfo().uri)
							.setDescription("Pedido por: " + currentTrack.getMember().getAsMention())
							.build()).queueAfter(3, TimeUnit.SECONDS);
					isFound = true;
					break;
				}
			}
		}
		if (!isFound) {
			currentChannel.sendMessageEmbeds(new EmbedBuilder()
					.setAuthor("Tocando agora:")
					.setTitle(currentTrack.getTrack().getInfo().title, currentTrack.getTrack().getInfo().uri)
					.setDescription("Pedido por: " + currentTrack.getMember().getAsMention())
					.build()).queueAfter(3, TimeUnit.SECONDS);
		}
	}

	@Override
	public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
		
		if (endReason.mayStartNext) {
			if (queue.isEmpty()) {
				currentTrack = new GadoAudioTrack();
				resetTimer("Cabou a música, to indo nessa rapazeada 😎", 5, TimeUnit.MINUTES);
			} else
				nextTrack();
		}
		
	}

	@Override
	public void onPlayerPause(AudioPlayer player) {
		resetTimer("Tomei uma Pausada por muito tempo, flw", 15, TimeUnit.MINUTES);
	}
	
	@Override
	public void onPlayerResume(AudioPlayer player) {
		this.timer.cancel();
	}
	
	private void resetTimer(String msg, int time, TimeUnit unit) {
		this.timer.cancel();
		this.timer = new Timer();
		this.timer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				guild.getAudioManager().closeAudioConnection();
				player.setPaused(false);
				clearQueue();
				connected = false;
				currentChannel.sendMessageEmbeds(new EmbedBuilder()
						.setDescription(msg)
						.build()).queue();
			}
			
		}, TimeUnit.MILLISECONDS.convert(time, unit));
	}
	
	public void leaveChannelOnIdle() {
		this.guild.getAudioManager().closeAudioConnection();
		this.currentChannel.sendMessageEmbeds(new EmbedBuilder()
				.setDescription("todo mundo foi embora, vazei 😟")
				.build()).queue();
	}
	
	public void shuffle() {
		List<GadoAudioTrack> tempQueue = new LinkedList<>();
		queue.drainTo(tempQueue);
		Collections.shuffle(tempQueue);
		queue.addAll(tempQueue);
	}
	
	@Override
	public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
		System.err.println("An exception occurred while trying to play the track, attempting to recover...");
		if (nOfTries >= 3) {
			System.err.println("Number of tries exceeded, skipping song");
			nextTrack();
		} else if (!exception.getMessage().contains("403")) {
			AudioTrack cloneTrack = track.makeClone();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
			System.err.println("Trying to replay the track...");
			player.startTrack(cloneTrack, false);
		}
		nOfTries++;
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

	public boolean isPlaying() {
		return !this.currentTrack.getSongName().equals("");
	}
	
	public void jumpToTrack(int trackIndex) {
		for (int i = 0; i < trackIndex-1; i++) queue.pop();
		nextTrack();
	}

	public VoiceChannel getVoiceChannel() {
		return voiceChannel;
	}

	public void setVoiceChannel(VoiceChannel voiceChannel) {
		this.voiceChannel = voiceChannel;
	}

	public boolean isConnected() {
		return connected;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}
}
