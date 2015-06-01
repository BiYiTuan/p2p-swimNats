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

import java.util.Random;
import java.lang.Math;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.swim.msg.PiggyBackElement;
import se.kth.swim.msg.Ping;
import se.kth.swim.msg.Pong;
import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.NetPing;
import se.kth.swim.msg.net.NetPong;
import se.kth.swim.msg.net.NetStatus;
import se.kth.swim.msg.net.NodeStatus;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class SwimComp extends ComponentDefinition {

    private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    
    private final NatedAddress selfAddress;
    private final Set<NatedAddress> neighbors;
    private final NatedAddress aggregatorAddress;
    private final Map<Integer,PiggyBackElement> aliveNodes;
    private final Map<Integer,PiggyBackElement> failedNodes;
    private final Map<Integer,PiggyBackElement> piggybacked;

    private UUID pingTimeoutId;
    private UUID statusTimeoutId;
    private UUID ackTimeoutId;
    private int receivedPings = 0;
    
    private Map<UUID,NatedAddress> ids = new HashMap<UUID,NatedAddress>();
    
    Double lamdalogn;

    public SwimComp(SwimInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} initiating...", selfAddress);
        this.neighbors = init.bootstrapNodes;
        this.aggregatorAddress = init.aggregatorAddress;
        this.aliveNodes=new HashMap<Integer,PiggyBackElement>();
        this.failedNodes=new HashMap<Integer,PiggyBackElement>();
        this.piggybacked=new HashMap<Integer,PiggyBackElement>();
        lamdalogn=((2) *Math.log10(neighbors.size()));
        for (NatedAddress address: neighbors){
        	this.aliveNodes.put(address.getId(),new PiggyBackElement(address,NodeStatus.ALIVE,0,calculateDisseminateTimes()));
        }
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handlePingTimeout, timer);
        subscribe(handleStatusTimeout, timer);
        subscribe(handleAckTimeout, timer);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            if (!neighbors.isEmpty()) {
            	log.info("{} starting...", new Object[]{selfAddress.getId()});
                schedulePeriodicPing();
            }
            schedulePeriodicStatus();
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress.getId()});
            if (pingTimeoutId != null) {
                cancelPeriodicPing();
            }
            if (statusTimeoutId != null) {
                cancelPeriodicStatus();
            }
        }

        
    };

    private Handler<NetPing> handlePing = new Handler<NetPing>() {

        @Override
        public void handle(NetPing event) {
        	log.info("{} received ping from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            receivedPings++;
            //if i get a ping from an unknown node, add it to the neighbor list
            if (!neighbors.contains(event.getHeader().getSource())){
            	neighbors.add(event.getHeader().getSource());
            	aliveNodes.put(event.getHeader().getSource().getId(),new PiggyBackElement(event.getHeader().getSource(),NodeStatus.ALIVE,0,calculateDisseminateTimes()));
            	//now i have a node to ping....
            	if (neighbors.size()>0){
            		schedulePeriodicPing();
            	}
            }
            log.info("{} sending pong to partner:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            //clear previous piggybacking 
            piggybacked.clear();
            piggybacked.putAll(aliveNodes);
            piggybacked.putAll(failedNodes);
            //piggyback information about changes
            Iterator<Map.Entry<Integer, PiggyBackElement>> entries = piggybacked.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<Integer, PiggyBackElement> entry = entries.next();
                if (entry.getValue().getDiseminateTimes()<0){
                	entries.remove();
                	
                }else {
                	entry.getValue().dicreaseDisseminateTimes();
                	entry.getValue().incrementCounter();
                }
            }
            log.info("{} piggybacking:{} to {}", new Object[]{selfAddress.getId(),piggybacked.size(), event.getHeader().getSource()});
            trigger(new NetPong(selfAddress,event.getHeader().getSource(),new Pong(piggybacked,event.getContent().getSn())),network);
        }

    };
    
    private Handler<NetPong> handlePong = new Handler<NetPong>(){

		@Override
		public void handle(NetPong event) {
			// TODO Auto-generated method stub
			log.info("{} received pong from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
			if ((event.getContent()!=null)&&(ids.containsValue(event.getSource())&&(ids.containsKey(event.getContent().getSn())))){
			log.info("piggybacked pong {}", new Object[]{event.getContent().toString()});
			cancelWaitingAck(event.getContent().getSn());
			//get the piggybacked elements
			Iterator<Map.Entry<Integer, PiggyBackElement>> entries = event.getContent().getElements().entrySet().iterator();
			//should merge received elements with my elements
            while (entries.hasNext()) {
            	 Map.Entry<Integer, PiggyBackElement> entry = entries.next();
                Integer key = entry.getKey();
                PiggyBackElement value = entry.getValue();
                if (value.getAddress().equals(selfAddress)){
                	continue;
                }
                else if ((value.getStatus()==NodeStatus.ALIVE)){
                	if (aliveNodes.containsKey(key)){
                		if ((value.getCount()>aliveNodes.get(key).getCount())){
                			aliveNodes.put(key, value);
                		}
                	}else if (failedNodes.containsKey(key)) {
                		if ((value.getCount()>failedNodes.get(key).getCount())){
                			value.setDiseminateTimes(calculateDisseminateTimes());
                			aliveNodes.put(key, value);
                		}
                	}else {
                		//totally new node
                		//neighbors.add(value.getAddress());
                		value.setDiseminateTimes(calculateDisseminateTimes());
                		aliveNodes.put(key, value);
                	}
                	//add to neigbors if it is not already there
                }else if (value.getStatus()==NodeStatus.FAILED){
                	//to be implemented later
                }
            }
			}
		}
    	
    };

    private Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

        @Override
        public void handle(PingTimeout event) {
        	//select random peer for bootstrap node
        	//should it be alive nodes only?
			Random random = new Random();
			List<Integer> keys = new ArrayList<Integer>(aliveNodes.keySet());
			Integer randomKey = keys.get(random.nextInt(aliveNodes.size()));
			PiggyBackElement value = aliveNodes.get(randomKey);
			log.info("{} sending ping to partner:{}", new Object[] {
					selfAddress.getId(), value.getAddress() });
		    scheduleWaitingAck();
		    ids.put(ackTimeoutId,value.getAddress());
			trigger(new NetPing(selfAddress, value.getAddress(), new Ping(ackTimeoutId)), network);
//        	int i=0;
//            for (NatedAddress partnerAddress : ) {
//            	if (i==node){
//               
//                
//                break;
//            	}
//            	i++;
//            }
        }

    };

    private Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {

        @Override
        public void handle(StatusTimeout event) {
            log.info("{} sending status to aggregator:{} alive {} failed {} total {}", new Object[]{selfAddress.getId(), aggregatorAddress,aliveNodes.size(), failedNodes.size(),neighbors.size()});
            trigger(new NetStatus(selfAddress, aggregatorAddress, new Status(receivedPings)), network);
        }

    };
    
    private Handler<AckTimeout> handleAckTimeout = new Handler<AckTimeout>(){

		@Override
		public void handle(AckTimeout event) {
			// TODO Auto-generated method stub
			log.info("{} timeout - no reply from: {} NODE FAILED!", new Object[]{selfAddress.getId(), ids.get(event.getTimeoutId())});
			Integer addressid = ids.get(event.getTimeoutId()).getId();
			for (Integer key : aliveNodes.keySet()) {
			    System.out.println("Key = " + key);
			}
			if (aliveNodes.containsKey(addressid)){
				PiggyBackElement element =(PiggyBackElement) aliveNodes.get(addressid);
				log.info("piggyback element: {}",element.toString());
				element.setDiseminateTimes(calculateDisseminateTimes());
				failedNodes.put(addressid, element);
				aliveNodes.remove(addressid);
			}
			ids.remove(event.getTimeoutId());
			cancelWaitingAck(event.getTimeoutId());
		}
    	
    };

    private void schedulePeriodicPing() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 1000);
        PingTimeout sc = new PingTimeout(spt);
        spt.setTimeoutEvent(sc);
        pingTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }
    
    private void scheduleWaitingAck() {
        ScheduleTimeout st = new ScheduleTimeout(3000);
        AckTimeout sc = new AckTimeout(st);
        st.setTimeoutEvent(sc);
        ackTimeoutId = sc.getTimeoutId();
        trigger(st, timer);
        //return ackTimeoutId;
    }
    
    private void cancelWaitingAck(UUID id) {
        CancelTimeout cpt = new CancelTimeout(id);
        ids.remove(id);
        trigger(cpt, timer);
    }

    private void cancelPeriodicPing() {
        CancelTimeout cpt = new CancelTimeout(pingTimeoutId);
        trigger(cpt, timer);
        pingTimeoutId = null;
    }

    private void schedulePeriodicStatus() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(10000, 10000);
        StatusTimeout sc = new StatusTimeout(spt);
        spt.setTimeoutEvent(sc);
        statusTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicStatus() {
        CancelTimeout cpt = new CancelTimeout(statusTimeoutId);
        trigger(cpt, timer);
        statusTimeoutId = null;
    }
    
    private int calculateDisseminateTimes(){
    	Double d = (2) *Math.log10(neighbors.size());
    	return (d.intValue());
    }

    public static class SwimInit extends Init<SwimComp> {

        private final NatedAddress selfAddress;
        private final Set<NatedAddress> bootstrapNodes;
        private final NatedAddress aggregatorAddress;

        public SwimInit(NatedAddress selfAddress, Set<NatedAddress> bootstrapNodes, NatedAddress aggregatorAddress) {
            this.selfAddress = selfAddress;
            this.bootstrapNodes = bootstrapNodes;
            this.aggregatorAddress = aggregatorAddress;
        }

		public NatedAddress getSelfAddress() {
			return selfAddress;
		}

		public Set<NatedAddress> getBootstrapNodes() {
			return bootstrapNodes;
		}

		public NatedAddress getAggregatorAddress() {
			return aggregatorAddress;
		}
    }

    private static class StatusTimeout extends Timeout {

        public StatusTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }

    private static class PingTimeout extends Timeout {

        public PingTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }
    
    private static class AckTimeout extends Timeout{

		protected AckTimeout(ScheduleTimeout request) {
			super(request);
			// TODO Auto-generated constructor stub
		}
    
    }
}
