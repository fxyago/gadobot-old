package br.gadobot.Player;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;

import br.gadobot.Handlers.AudioPlayerSendHandler;

public class GuildMusicManager {

	public final AudioPlayer player;
	
	public final TrackScheduler scheduler;
	
	public GuildMusicManager(AudioPlayerManager manager) {
		player = manager.createPlayer();
		scheduler = new TrackScheduler(player);
		player.addListener((AudioEventListener) scheduler);
	}
	
	public GuildMusicManager() {
		this.scheduler = null;
		this.player = null;
	}

	public AudioPlayerSendHandler getSendHandler() {
		return new AudioPlayerSendHandler(player);
	}
	
}
