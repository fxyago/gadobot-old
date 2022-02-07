package br.gadobot.player;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.Member;

public class GadoAudioTrack {
	
	private AudioTrack track;
	private Member member;
	private String songName;
	
	public GadoAudioTrack(AudioTrack track, Member member, String songName) {
		this.track = track;
		this.member = member;
		this.songName = songName;
	}
	
	public GadoAudioTrack(Member member, String songName) {
		this.track = null;
		this.member = member;
		this.songName = songName;
	}
	
	public GadoAudioTrack() {
		this.track = null;
		this.member = null;
		this.songName = "";
	}
	
	public AudioTrack getTrack() {
		return track;
	}
	
	public void setTrack(AudioTrack track) {
		this.track = track;
	}
	
	public Member getMember() {
		return member;
	}
	
	public String getSongName() {
		return songName;
	}
	
}
