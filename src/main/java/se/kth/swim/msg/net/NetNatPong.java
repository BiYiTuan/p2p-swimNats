package se.kth.swim.msg.net;

import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetNatPong extends NetMsg<NatPong>{

	public NetNatPong(NatedAddress src, NatedAddress dst, NatPong content) {
		super(src, dst, content);
		// TODO Auto-generated constructor stub
	}

	@Override
	public NetMsg copyMessage(Header<NatedAddress> newHeader) {
		// TODO Auto-generated method stub
		return new NetNatPong(newHeader,getContent());
	}

	public NetNatPong(Header<NatedAddress> header, NatPong content) {
		super(header, content);
		// TODO Auto-generated constructor stub
	}

}
