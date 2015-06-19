package se.kth.swim.msg.net;

import java.util.Map;
import java.util.UUID;

import se.kth.swim.msg.FDMessage;
import se.kth.swim.msg.PiggyBackElement;

public class NatPong {

	private UUID sn;

	public NatPong(UUID sn) {
		// TODO Auto-generated constructor stub
		this.sn=sn;
	}

	public UUID getSn() {
		return sn;
	}

	public void setSn(UUID sn) {
		this.sn = sn;
	}

	
}
