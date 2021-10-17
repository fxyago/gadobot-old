package GadoBot;

import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import net.dv8tion.jda.api.entities.TextChannel;

public class TrackScheduler extends AudioEventAdapter {

	private final AudioPlayer player;
	private final BlockingQueue<AudioTrack> queue;
	private AudioTrack currentTrack;
	
	public TrackScheduler(AudioPlayer player) {
		this.player = player;
		this.queue = new LinkedBlockingQueue<>();
	}

	public void queue(AudioTrack track) {
		if (!player.startTrack(track, true)) {
			queue.offer(track);
		}
		if (queue.isEmpty()) {
			currentTrack = track;
		}
	}

	public void nextTrack() {
		currentTrack = queue.peek();
		player.startTrack(queue.poll(), false);
	}
	
	public String list() {
		int i = 1;
		if (!queue.isEmpty()) {
			String lista = i + " - " + currentTrack.getInfo().author + " - " + currentTrack.getInfo().title + " (Tocando agora)";
			for (AudioTrack audioTrack : queue) {
				i++;
				lista += "\n" + i + " - " + audioTrack.getInfo().title;
			}
			return lista;
		} else {
			return i + " - " + currentTrack.getInfo().author + " - " + currentTrack.getInfo().title;
		}
	}
								
	public AudioTrack remove(int trackIndex) {
		
		LinkedList<AudioTrack> trackTemp = new LinkedList<>();
		queue.drainTo(trackTemp);
		AudioTrack trackRemoved = trackTemp.get(trackIndex);
		trackTemp.remove(trackIndex);
		trackTemp.forEach((t) -> queue.offer(t));
		return trackRemoved;
	}
	
	public AudioTrack move(int moveFrom, int moveTo) {
		
		LinkedList<AudioTrack> trackTemp = new LinkedList<>();
		queue.drainTo(trackTemp);
		AudioTrack trackMoved = trackTemp.get(moveFrom);
		trackTemp.remove(moveFrom);
		trackTemp.add(moveTo, trackMoved);
		trackTemp.forEach((t) -> queue.offer(t));
		return trackMoved;
	}

	public void emptyQueue() {
		queue.clear();
	}
	
	public void fairQueue() {
		// TODO fairqueue
	}

	public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {

		if (endReason.mayStartNext) {
			
			if (queue.isEmpty()) {
				currentTrack = null;
			} else {
				nextTrack();
			}
		}
		
	}
	
	public AudioTrack getCurrentTrack() {
		return currentTrack;
	}
	
}
