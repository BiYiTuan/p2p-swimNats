package se.kth.swim.msg.net;

import java.util.UUID;

public class NatPing {

	private UUID sn;

	public NatPing(UUID sn) {
		this.sn=sn;
		// TODO Auto-generated constructor stub
	}

	public UUID getSn() {
		return sn;
	}

	public void setSn(UUID sn) {
		this.sn = sn;
	}

}
