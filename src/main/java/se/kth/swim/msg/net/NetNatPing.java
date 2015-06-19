package se.kth.swim.msg.net;

import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetNatPing extends NetMsg<NatPing>{

	public NetNatPing(NatedAddress src, NatedAddress dst, NatPing content) {
		super(src, dst, content);
		// TODO Auto-generated constructor stub
	}

	public NetNatPing(Header<NatedAddress> header, NatPing content) {
		super(header, content);
		// TODO Auto-generated constructor stub
	}

	@Override
	public NetMsg copyMessage(Header<NatedAddress> newHeader) {
		// TODO Auto-generated method stub
		return new NetNatPing(newHeader,getContent());
	}

	@Override
	public NatPing getContent() {
		// TODO Auto-generated method stub
		return super.getContent();
	}

	@Override
	protected Object clone() throws CloneNotSupportedException {
		// TODO Auto-generated method stub
		return super.clone();
	}

}
