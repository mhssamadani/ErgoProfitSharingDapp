package staking

object Scripts {

  lazy val incomeScript: String =
    s"""{
       |  val incomeMerge =
       |    allOf(Coll(
       |      INPUTS.size == 10,
       |      OUTPUTS.size == 2,
       |      OUTPUTS(0).propositionBytes == SELF.propositionBytes,
       |      OUTPUTS(1).value <= maxFee
       |      // ERG can not burn so more checking is not required
       |    ))
       |  val tokenMerge =
       |    if(SELF.tokens.size > 0) {
       |      allOf(Coll(
       |        OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1,
       |        OUTPUTS(0).tokens(0)._2 == INPUTS(0).tokens(0)._2 + INPUTS(1).tokens(0)._2 + INPUTS(2).tokens(0)._2 + INPUTS(3).tokens(0)._2 +
       |                                INPUTS(4).tokens(0)._2 + INPUTS(5).tokens(0)._2 + INPUTS(6).tokens(0)._2 + INPUTS(7).tokens(0)._2 +
       |                                INPUTS(8).tokens(0)._2 + INPUTS(9).tokens(0)._2
       |      ))
       |    } else {true}
       |
       |  // TODO
       |  val incomeDistribution =
       |    // Config box checking
       |    OUTPUTS(0).tokens(0)._1 == configNFT
       |
       |  sigmaProp((incomeMerge && tokenMerge) || incomeDistribution)
       |}""".stripMargin

  // Registers R4: Coll[Checkpoint, ErgShare, TokenShare, Fee]
  // Tokens 0: DistributionToken, 1: IncomeToken (optional)
  lazy val distributionScript: String =
    s"""{
       |  val checkpoint = SELF.R4[Coll[Long]].get(0)
       |  val ergShare = SELF.R4[Coll[Long]].get(1)
       |  val tokenShare = SELF.R4[Coll[Long]].get(2)
       |  val fee = SELF.R4[Coll[Long]].get(3)
       |  val tokenDistribution = SELF.tokens.size > 1
       |
       |  if(SELF.value > fee) {
       |    // Paying the share to ticket holders
       |    // INPUTS: SELF, Ticket --> OUTPUTS: SELF-Replication, Ticket-Replication, Payment
       |    val paymentCheck = allOf(Coll(
       |      // Size checking
       |      INPUTS.size == 2,
       |      OUTPUTS.size == 4,
       |      // Self Replication checking
       |      OUTPUTS(0).R4[Coll[Long]].get == SELF.R4[Coll[Long]].get,
       |      OUTPUTS(0).propositionBytes == SELF.propositionBytes,
       |      OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1,
       |      // Input Ticket checking
       |      INPUTS(1).tokens(0)._1 == lockingToken,
       |      INPUTS(1).tokens(1)._1 == stakingToken,
       |      INPUTS(1).R4[Coll[Long]].get(1) == checkpoint,
       |      OUTPUTS(1).R4[Coll[Long]].get(1) >= checkpoint + 1,
       |      // Ergs checking
       |      OUTPUTS(0).value == SELF.value - (INPUTS(1).tokens(1)._2 * ergShare) - fee,
       |      OUTPUTS(2).value == INPUTS(1).tokens(1)._2 * ergShare
       |    ))
       |    sigmaProp(
       |      if(tokenDistribution) {
       |        allOf(Coll(
       |          paymentCheck,
       |          // Tokens checking
       |          OUTPUTS(0).tokens(1)._1 == SELF.tokens(1)._1,
       |          OUTPUTS(0).tokens(1)._2 == SELF.tokens(1)._2 - (INPUTS(1).tokens(1)._2 * tokenShare),
       |          OUTPUTS(2).tokens(0)._1 == SELF.tokens(1)._1,
       |          OUTPUTS(2).tokens(0)._2 == INPUTS(1).tokens(1)._2 * tokenShare
       |        ))
       |      } else {paymentCheck}
       |    )
       |  }
       |  else {
       |    // distribution Token Redeem
       |    // INPUTS: Config, SELF --> OUTPUTS: Config-Replication
       |    sigmaProp(
       |      // Size checking
       |      INPUTS.size == 2 &&
       |      OUTPUTS.size == 2 &&
       |      // Config box checking
       |      INPUTS(0).tokens(0)._1 == configNFT
       |    )
       |  }
       |}""".stripMargin


  // Registers R4: [initialCheckpoint, checkpoint], R5: recipientAddress, R6: ReservedTokenId
  // Tokens 0: LockingToken, 1: StakingToken
  lazy val ticketScript: String =
    s"""{
       |  val checkpoint = SELF.R4[Coll[Long]].get(1)
       |  val reservedToken = SELF.R6[Coll[Byte]].get
       |
       |  // spend to get income
       |  // INPUTS: Distribution, SELF --> OUTPUTS: Distribution-Replication, SELF-Replication, Payment
       |  if(INPUTS.size < 3) {
       |    val getIncome = allOf(Coll(
       |      // distribution box checking
       |      OUTPUTS(0).tokens(0)._1 == distributionToken,
       |      // Self replication checking
       |      OUTPUTS(1).R4[Coll[Long]].get(0) == SELF.R4[Coll[Long]].get(0),
       |      OUTPUTS(1).R4[Coll[Long]].get(1) <= checkpoint + 1,
       |      OUTPUTS(1).R6[Coll[Byte]].get == reservedToken,
       |      OUTPUTS(1).value == SELF.value,
       |      OUTPUTS(1).tokens(0)._1 == SELF.tokens(0)._1,
       |      OUTPUTS(1).tokens(1)._1 == SELF.tokens(1)._1,
       |      OUTPUTS(1).tokens(0)._2 == SELF.tokens(0)._2,
       |      OUTPUTS(1).propositionBytes == SELF.propositionBytes,
       |      // Check payment address
       |      OUTPUTS(2).propositionBytes == SELF.R5[Coll[Byte]].get
       |    ))
       |    sigmaProp(getIncome)
       |  }
       |  else {
       |    // unlocking staking tokens
       |    // INPUTS: Config, SELF, ReservedTokens --> OUTPUTS: Config-Replication, UnlockedStakingTokens
       |    val unlocking = allOf(Coll(
       |      // Config Box checking
       |      INPUTS(0).tokens(0)._1 == configNFT,
       |      // Reserve Tokens Checking
       |      INPUTS(2).tokens(0)._1 == reservedToken,
       |      // Staking token redeem checking
       |      OUTPUTS(1).tokens(0)._1 == SELF.tokens(1)._1,
       |      OUTPUTS(1).tokens(0)._2 == SELF.tokens(1)._2,
       |    ))
       |    sigmaProp(unlocking)
       |  }
       |}""".stripMargin

  // Registers R4: Coll[CheckPoint, ErgShare, TokenShare, TicketCount, StakeCount, Fee
  // Tokens 0: configNFT, 1: DistributionTokens, 2: LockingTokens
  lazy val configScript: String =
    s"""{
       |  sigmaProp(true)
       |}""".stripMargin

}
