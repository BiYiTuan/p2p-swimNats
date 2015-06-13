package se.kth.swim.msg.net;

import java.util.Set;

import se.sics.kompics.KompicsEvent;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetNatResponse implements KompicsEvent{

	private Set<NatedAddress> parents;

	public NetNatResponse(Set<NatedAddress> parents) {
		super();
		this.parents = parents;
	}

	public Set<NatedAddress> getParents() {
		return parents;
	}

	public void setParents(Set<NatedAddress> parents) {
		this.parents = parents;
	}
}
