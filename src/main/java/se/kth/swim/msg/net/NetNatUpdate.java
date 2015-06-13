package se.kth.swim.msg.net;

import se.sics.kompics.KompicsEvent;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetNatUpdate implements KompicsEvent {
	private final NatedAddress selfAddress;

	public NetNatUpdate(NatedAddress selfAddress) {
		this.selfAddress = selfAddress;
	}

	public NatedAddress getSelfAddress() {
		return selfAddress;
	}
}
