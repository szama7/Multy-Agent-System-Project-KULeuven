package CNetProtocol;

import com.github.rinde.rinsim.core.model.comm.MessageContents;
import com.google.common.base.Optional;

public class ACLStructure implements MessageContents {

	public static enum Informative {
		CFP, REFUSE, PROPOSE, REJECT_PROPOSAL, ACCEPT_PROPOSAL, FAILURE, AGREE
	}

	private final Informative informative;
	private final Optional<Double> bid;
	private final Long deadline;

	public ACLStructure(Informative informative) {
		this.informative = informative;
		this.bid = Optional.absent();
		this.deadline = null;
	}

	public ACLStructure(Informative informative, double bid) {
		this.informative = informative;
		this.bid = Optional.of(bid);
		this.deadline = null;
	}

	public ACLStructure(Informative informative, Long deadline) {
		this.informative = informative;
		this.bid = Optional.absent();
		this.deadline = deadline;
	}

	public Informative getInformative() {
		return informative;
	}

	public Optional<Double> getBid() {
		return bid;
	}

	public Long getDeadline() {
		return deadline;
	}

}
