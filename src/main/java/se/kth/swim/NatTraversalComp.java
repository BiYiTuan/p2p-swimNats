/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.kth.swim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.swim.croupier.CroupierPort;
import se.kth.swim.croupier.internal.CroupierShuffle.Basic;
import se.kth.swim.croupier.msg.CroupierSample;
import se.kth.swim.croupier.util.Container;
import se.kth.swim.msg.net.NatPing;
import se.kth.swim.msg.net.NatPong;
import se.kth.swim.msg.net.NatPort;
import se.kth.swim.msg.net.NetMsg;
import se.kth.swim.msg.net.NetNatPing;
import se.kth.swim.msg.net.NetNatPong;
import se.kth.swim.msg.net.NetNatRequest;
import se.kth.swim.msg.net.NetNatResponse;
import se.kth.swim.msg.net.NetNatUpdate;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Header;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatType;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;
import se.sics.p2ptoolbox.util.network.impl.RelayHeader;
import se.sics.p2ptoolbox.util.network.impl.SourceHeader;

/**
 *
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class NatTraversalComp extends ComponentDefinition {

    private static final Logger log = LoggerFactory.getLogger(NatTraversalComp.class);
    private Negative<Network> local = provides(Network.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<CroupierPort> croupier = requires(CroupierPort.class);
    private Positive<Timer> timer = requires(Timer.class);
    private Negative<NatPort> nat = provides(NatPort.class);

    private NatedAddress selfAddress;
    private final Random rand;
    
    private Map<UUID,NatedAddress> ackIds;
    private  List<NatedAddress> croupierSample;
    
    private UUID natTimeout;

    public NatTraversalComp(NatTraversalInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} {} initiating...", new Object[]{selfAddress.getId(), (selfAddress.isOpen() ? "OPEN" : "NATED")});
        this.ackIds=new HashMap<UUID,NatedAddress>();
        this.rand = new Random(init.seed);
        this.croupierSample = new ArrayList<NatedAddress>();
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleIncomingMsg, network);
        subscribe(handleOutgoingMsg, local);
        subscribe(handleNatTimeout,timer);
        subscribe(handleFailureTimeout,timer);
        subscribe(handleCroupierSample, croupier);
        subscribe(handleNetNatPing,network);
        subscribe(handleNetNatPong,network);
        subscribe(handleNetNatResponse,nat);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            log.info("{} starting...", new Object[]{selfAddress.getId()});
            if (!selfAddress.isOpen()){
            	natTimeout = scheduleNatTimeout();
            }
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress.getId()});
            for (UUID id : ackIds.keySet()){
            	cancelFailureTimeout(id);
            }
            cancelNatTimeout(natTimeout);
        }

    };

    private Handler<NetMsg<Object>> handleIncomingMsg = new Handler<NetMsg<Object>>() {

        @Override
        public void handle(NetMsg<Object> msg) {
            log.trace("{} received msg:{}", new Object[]{selfAddress.getId(), msg});
            Header<NatedAddress> header = msg.getHeader();
            if (header instanceof SourceHeader) {
                if (!selfAddress.isOpen()) {
                    throw new RuntimeException("source header msg received on nated node - nat traversal logic error");
                }
                SourceHeader<NatedAddress> sourceHeader = (SourceHeader<NatedAddress>) header;
                if (sourceHeader.getActualDestination().getParents().contains(selfAddress)) {
                    log.info("{} relaying message for:{}", new Object[]{selfAddress.getId(), sourceHeader.getSource()});
                    RelayHeader<NatedAddress> relayHeader = sourceHeader.getRelayHeader();
                    trigger(msg.copyMessage(relayHeader), network);
                    return;
                } else {
                    log.warn("{} received weird relay message:{} - dropping it", new Object[]{selfAddress.getId(), msg});
                    return;
                }
            } else if (header instanceof RelayHeader) {
                if (selfAddress.isOpen()) {
                    throw new RuntimeException("relay header msg received on open node - nat traversal logic error");
                }
                RelayHeader<NatedAddress> relayHeader = (RelayHeader<NatedAddress>) header;
                log.info("{} delivering relayed message:{} from:{}", new Object[]{selfAddress.getId(), msg, relayHeader.getActualSource()});
                Header<NatedAddress> originalHeader = relayHeader.getActualHeader();
                trigger(msg.copyMessage(originalHeader), local);
                return;
            } else {
                log.info("{} delivering direct message:{} from:{}", new Object[]{selfAddress.getId(), msg, header.getSource()});
                trigger(msg, local);
                return;
            }
        }

    };

    private Handler<NetMsg<Object>> handleOutgoingMsg = new Handler<NetMsg<Object>>() {

        @Override
        public void handle(NetMsg<Object> msg) {
            log.trace("{} sending msg:{}", new Object[]{selfAddress.getId(), msg});
            Header<NatedAddress> header = msg.getHeader();
            if(header.getDestination().isOpen()) {
                log.info("{} sending direct message:{} to:{}", new Object[]{selfAddress.getId(), msg, header.getDestination()});
                trigger(msg, network);
                return;
            } else {
                if(header.getDestination().getParents().isEmpty()) {
                    throw new RuntimeException("nated node with no parents");
                }
                NatedAddress parent = randomNode(header.getDestination().getParents());
                SourceHeader<NatedAddress> sourceHeader = new SourceHeader(header, parent);
                log.info("{} sending message:{} to relay:{}", new Object[]{selfAddress.getId(), msg, parent});
                trigger(msg.copyMessage(sourceHeader), network);
                return;
            }
        }

    };
    
    private Handler<FailureTimeout> handleFailureTimeout = new Handler<FailureTimeout>(){

		@Override
		public void handle(FailureTimeout event) {
			// TODO Auto-generated method stub
			log.info("{} didn't receive Nat Pong {} is dead!", new Object[]{selfAddress.getId(),ackIds.get(event.getTimeoutId())});
			//select new parents
			ackIds.remove(event.getTimeoutId());
			if (croupierSample.size()>1){
				for (UUID ids:ackIds.keySet()){
					cancelFailureTimeout(ids);
					ackIds.remove(ids);
				}
				Set<NatedAddress> parents=new HashSet<NatedAddress>();
				if (croupierSample.size()==1){
					parents.add(croupierSample.get(0));
				}else {
					Random random = new Random();
					int rand = random.nextInt(croupierSample.size());
					parents.add(croupierSample.get(rand-1));
					rand = random.nextInt(croupierSample.size());
					parents.add(croupierSample.get(rand-1));
					
				}
				if (parents!=null){
						trigger(new NetNatRequest(parents), nat);						
				}
			}else {
				log.info("{} could not find croupier node",selfAddress.getId());
			}

			
		}
    	
    };
    
    private Handler<NatTimeout> handleNatTimeout = new Handler<NatTimeout>(){

		@Override
		public void handle(NatTimeout event) {
			// TODO Auto-generated method stub
			Set<NatedAddress> parents = selfAddress.getParents();
			for (NatedAddress parent:parents){
				UUID natId = scheduleFailureTimeout(parent);
				log.info(" {} sends periodic Nat Ping to relay {}",new Object[]{selfAddress.getId(),parent.getId()});
				trigger(new NetNatPing(selfAddress, parent, new NatPing(natId,null)),network);
			}
		}
    	
    };
    
    private Handler<NetNatPing> handleNetNatPing = new Handler<NetNatPing>(){

		@Override
		public void handle(NetNatPing event) {
			// TODO Auto-generated method stub
			log.info(" {} received periodic Nat Ping to from NatedNode {}",new Object[]{selfAddress.getId(),event.getSource().getId()});
			trigger(new NetNatPong(selfAddress,event.getSource(),new NatPong(event.getContent().getSn(),null)), network);
		}
    };
    
    private Handler<NetNatPong> handleNetNatPong = new Handler<NetNatPong>(){

		@Override
		public void handle(NetNatPong event) {
			// TODO Auto-generated method stub
			log.info("{} received nat pong from Relay parent {}",new Object[]{selfAddress.getId(),event.getSource().getId()});
			cancelFailureTimeout(event.getContent().getSn());
		}
    	
    };
    
    private Handler<NetNatResponse> handleNetNatResponse = new Handler<NetNatResponse>(){

		@Override
		public void handle(NetNatResponse event) {
			// TODO Auto-generated method stub
			selfAddress = new BasicNatedAddress(new BasicAddress(
	    			selfAddress.getIp(), 12345, selfAddress.getId()),
	    			NatType.NAT, new HashSet<NatedAddress>(event.getParents()));
	      
	      log.debug("Node {} new parents are: {}", selfAddress.getId(), selfAddress.getParents());
	      
	      trigger(new NetNatUpdate(selfAddress), nat);
		}
    	
    };
    
    private UUID scheduleNatTimeout(){
    	SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 1000);
        NatTimeout sc = new NatTimeout(spt);
        spt.setTimeoutEvent(sc);
        natTimeout = sc.getTimeoutId();
        trigger(spt, timer);
        return natTimeout;
    }
    
    private UUID scheduleFailureTimeout(NatedAddress address){
    	ScheduleTimeout spt = new ScheduleTimeout(2000);
        FailureTimeout sc = new FailureTimeout(spt);
        spt.setTimeoutEvent(sc);
        ackIds.put(sc.getTimeoutId(),address);
        trigger(spt, timer);
        return natTimeout;
    }
    
    private void cancelNatTimeout(UUID id){
    	CancelTimeout cpt = new CancelTimeout(id);
        trigger(cpt, timer);
    }
    
    private void cancelFailureTimeout(UUID id){
    	CancelTimeout cpt = new CancelTimeout(id);
        ackIds.remove(id);
        trigger(cpt, timer);
    }
    
    private Handler handleCroupierSample = new Handler<CroupierSample>() {
        @Override
        public void handle(CroupierSample event) {
            log.info("{} croupier public nodes:{}", selfAddress.getBaseAdr(), event.publicSample);
            //use this to change parent in case it died
			croupierSample.clear();
			Iterator<Container<NatedAddress, Object>> iterator = event.publicSample.iterator();
			while (iterator.hasNext()) {
				croupierSample.add(iterator.next().getSource());
			}
        }
    };
    
    private NatedAddress randomNode(Set<NatedAddress> nodes) {
        int index = rand.nextInt(nodes.size());
        Iterator<NatedAddress> it = nodes.iterator();
        while(index > 0) {
            it.next();
            index--;
        }
        return it.next();
    }

    public static class NatTraversalInit extends Init<NatTraversalComp> {

        public final NatedAddress selfAddress;
        public final long seed;

        public NatTraversalInit(NatedAddress selfAddress, long seed) {
            this.selfAddress = selfAddress;
            this.seed = seed;
        }
    }
    
    private static class NatTimeout extends Timeout{

		protected NatTimeout(SchedulePeriodicTimeout request) {
			super(request);
			// TODO Auto-generated constructor stub
		}
    	
    }
    
    private static class FailureTimeout extends Timeout{

		protected FailureTimeout(ScheduleTimeout request) {
			super(request);
			// TODO Auto-generated constructor stub
		}
    	
    }
}
