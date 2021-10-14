package staking

object Scripts {

  lazy val incomeScript: String =
    s"""{
       |  if(OUTPUTS.size == 2) {
       |    // merging income boxes
       |    // INPUTS: incomes[10,100) --> OUTPUTS: income
       |    val incomeMerge =
       |      allOf(Coll(
       |        INPUTS.size >= 10,
       |        INPUTS.size < 100,
       |        OUTPUTS(0).propositionBytes == SELF.propositionBytes,
       |        OUTPUTS(1).value <= maxFee
       |        // ERG can not burn so more checking is not required
       |      ))
       |    val tokenMerge =
       |      if(SELF.tokens.size > 0) {
       |        val totalToken = INPUTS.fold(0L, {(x:Long, b:Box) => x + b.tokens(0)._2})
       |        allOf(Coll(
       |          OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1,
       |          OUTPUTS(0).tokens(0)._2 == totalToken
       |        ))
       |      } else {true}
       |    sigmaProp(incomeMerge && tokenMerge)
       |  }
       |  else{
       |    // spending income
       |    // INPUTS: config, SELF(income) --> OUTPUTS: config, distribution, income[optional]
       |    // Config box checking
       |    val configCheck = OUTPUTS(0).tokens(0)._1 == configNFT
       |    // Erg and token remainder checking
       |    val fee = OUTPUTS(0).R4[Coll[Long]].get(5)
       |    val ergRemainderCheck =
       |      if(OUTPUTS(1).value + fee < SELF.value){
       |        OUTPUTS(2).value == SELF.value - (OUTPUTS(1).value + fee) &&
       |        OUTPUTS(2).propositionBytes == SELF.propositionBytes
       |      } else {true}
       |    val tokenRemainderCheck =
       |      if(SELF.tokens.size > 0){
       |        OUTPUTS(1).tokens(1)._2 == SELF.tokens(0)._2 ||
       |        OUTPUTS(2).tokens(0)._2 == SELF.tokens(0)._2 - OUTPUTS(1).tokens(1)._2
       |      } else {true}
       |    sigmaProp(configCheck && ergRemainderCheck && tokenRemainderCheck)
       |  }
       |}""".stripMargin

  // Registers R4: Coll[Checkpoint, ErgShare, TokenShare, Fee], R5: TicketCount
  // Tokens 0: DistributionToken, 1: IncomeToken (optional)
  lazy val distributionScript: String =
    s"""{
       |  val checkpoint = SELF.R4[Coll[Long]].get(0)
       |  val ergShare = SELF.R4[Coll[Long]].get(1)
       |  val tokenShare = SELF.R4[Coll[Long]].get(2)
       |  val fee = SELF.R4[Coll[Long]].get(3)
       |  val ticketCount = SELF.R5[Long].get
       |  val tokenDistribution = SELF.tokens.size > 1
       |
       |  if(ticketCount > 0) {
       |    // Paying the share to ticket holders
       |    // INPUTS: SELF(Distribution), Ticket --> OUTPUTS: Distribution, Ticket, Payment
       |    val paymentCheck = allOf(Coll(
       |      // Size checking
       |      OUTPUTS.size == 4,
       |      // Self Replication checking
       |      OUTPUTS(0).R4[Coll[Long]].get == SELF.R4[Coll[Long]].get,
       |      OUTPUTS(0).R5[Long].get == ticketCount - 1,
       |      OUTPUTS(0).propositionBytes == SELF.propositionBytes,
       |      OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1,
       |      // Input Ticket checking
       |      INPUTS(1).tokens(0)._1 == lockingToken,
       |      INPUTS(1).tokens(1)._1 == stakingToken,
       |      INPUTS(1).R4[Coll[Long]].get(1) == checkpoint,
       |      OUTPUTS(1).R4[Coll[Long]].get(1) >= checkpoint + 1,
       |      // Ergs checking
       |      OUTPUTS(0).value == SELF.value - (INPUTS(1).tokens(1)._2 * ergShare),
       |    ))
       |    sigmaProp(
       |      if(tokenDistribution) {
       |        val outputTokenCheck =
       |          if(SELF.tokens(1)._2 - (INPUTS(1).tokens(1)._2 * tokenShare) > 0) {
       |            OUTPUTS(0).tokens(1)._1 == SELF.tokens(1)._1 &&
       |            OUTPUTS(0).tokens(1)._2 == SELF.tokens(1)._2 - (INPUTS(1).tokens(1)._2 * tokenShare)
       |          } else {true}
       |        allOf(Coll(
       |          paymentCheck,
       |          // Tokens checking
       |          outputTokenCheck,
       |          OUTPUTS(2).tokens(0)._1 == SELF.tokens(1)._1,
       |          OUTPUTS(2).tokens(0)._2 == INPUTS(1).tokens(1)._2 * tokenShare
       |        ))
       |      } else {paymentCheck}
       |    )
       |  }
       |  else {
       |    // distribution Token Redeem
       |    // INPUTS: Config, SELF(Distribution) --> OUTPUTS: Config
       |    sigmaProp(
       |      // Size checking
       |      INPUTS.size == 2 &&
       |      OUTPUTS.size == 2 &&
       |      // Config box checking
       |      INPUTS(0).tokens(0)._1 == configNFT
       |    )
       |  }
       |}""".stripMargin


  // Registers R4: [initialCheckpoint, checkpoint, fee, minBoxVal], R5: recipientAddress, R6: ReservedTokenId
  // Tokens 0: LockingToken, 1: StakingToken
  lazy val ticketScript: String =
    s"""{
       |  val checkpoint = SELF.R4[Coll[Long]].get(1)
       |  val fee = SELF.R4[Coll[Long]].get(2)
       |  val minBoxVal = SELF.R4[Coll[Long]].get(3)
       |  val reservedToken = SELF.R6[Coll[Byte]].get
       |
       |  if(INPUTS.size < 3) {
       |    if(OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1){
       |      // fee charging
       |      // INPUTS: SELF(Ticket), charge --> OUTPUTS: Ticket
       |      val charging = allOf(Coll(
       |        // Self replication checking
       |        OUTPUTS(0).R4[Coll[Long]].get == SELF.R4[Coll[Long]].get,
       |        OUTPUTS(0).R6[Coll[Byte]].get == reservedToken,
       |        OUTPUTS(0).value > SELF.value,
       |        OUTPUTS(0).tokens(1)._1 == SELF.tokens(1)._1,
       |        OUTPUTS(0).tokens(1)._2 == SELF.tokens(1)._2,
       |        OUTPUTS(0).propositionBytes == SELF.propositionBytes
       |        ))
       |      sigmaProp(charging)
       |    }
       |    else{
       |      // spend to get income
       |      // INPUTS: Distribution, SELF(Ticket) --> OUTPUTS: Distribution, Ticket, Payment
       |      val ergShare = INPUTS(0).R4[Coll[Long]].get(1)
       |      val getIncome = allOf(Coll(
       |        // distribution box checking
       |        OUTPUTS(0).tokens(0)._1 == distributionToken,
       |        // Self replication checking
       |        OUTPUTS(1).R4[Coll[Long]].get(0) == SELF.R4[Coll[Long]].get(0),
       |        OUTPUTS(1).R4[Coll[Long]].get(1) <= checkpoint + 1,
       |        OUTPUTS(1).R4[Coll[Long]].get(2) == fee,
       |        OUTPUTS(1).R4[Coll[Long]].get(3) == minBoxVal,
       |        OUTPUTS(1).R6[Coll[Byte]].get == reservedToken,
       |        OUTPUTS(1).value == SELF.value - fee - minBoxVal,
       |        OUTPUTS(1).tokens(0)._1 == SELF.tokens(0)._1,
       |        OUTPUTS(1).tokens(1)._1 == SELF.tokens(1)._1,
       |        OUTPUTS(1).tokens(1)._2 == SELF.tokens(1)._2,
       |        OUTPUTS(1).propositionBytes == SELF.propositionBytes,
       |        // Check payment
       |        OUTPUTS(2).propositionBytes == SELF.R5[Coll[Byte]].get,
       |        OUTPUTS(2).value == SELF.tokens(1)._2 * ergShare + minBoxVal
       |      ))
       |      sigmaProp(getIncome)
       |    }
       |  }
       |  else {
       |    // unlocking staking tokens
       |    // INPUTS: Config, SELF(Ticket), ReservedTokens --> OUTPUTS: Config, UnlockedStakingTokens
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

  // Registers R4: Coll[CheckPoint, MinErgShare, MinTokenShare, TicketCount, StakeCount, Fee, MinTicketValue, MinBoxVal]
  // Tokens 0: configNFT, 1: DistributionTokens, 2: LockingTokens
  lazy val configScript: String =
    s"""{
       |  val checkpoint = SELF.R4[Coll[Long]].get(0)
       |  val minErgShare = SELF.R4[Coll[Long]].get(1)
       |  val minTokenShare = SELF.R4[Coll[Long]].get(2)
       |  val ticketCount = SELF.R4[Coll[Long]].get(3)
       |  val stakeCount = SELF.R4[Coll[Long]].get(4)
       |  val fee = SELF.R4[Coll[Long]].get(5)
       |  val minTicketValue = SELF.R4[Coll[Long]].get(6)
       |  val minBoxVal = SELF.R4[Coll[Long]].get(7)
       |  val selfReplication = allOf(Coll(
       |    OUTPUTS(0).value == SELF.value,
       |    OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1,
       |    OUTPUTS(0).tokens(1)._1 == SELF.tokens(1)._1,
       |    OUTPUTS(0).tokens(2)._1 == SELF.tokens(2)._1,
       |    OUTPUTS(0).propositionBytes == SELF.propositionBytes,
       |    OUTPUTS(0).R4[Coll[Long]].get(1) == minErgShare,
       |    OUTPUTS(0).R4[Coll[Long]].get(2) == minTokenShare,
       |    OUTPUTS(0).R4[Coll[Long]].get(5) == fee,
       |    OUTPUTS(0).R4[Coll[Long]].get(6) == minTicketValue,
       |    OUTPUTS(0).R4[Coll[Long]].get(7) == minBoxVal,
       |  ))
       |
       |  if(OUTPUTS(0).tokens(1)._2 == SELF.tokens(1)._2 + 1) {
       |    // distribution token redeem
       |    // INPUTS: SELF(Config), Distribution --> OUTPUTS: SELF-Replication
       |    sigmaProp(allOf(Coll(
       |      // config setting checking
       |      selfReplication,
       |      OUTPUTS(0).tokens(2)._2 == SELF.tokens(2)._2,
       |      OUTPUTS(0).R4[Coll[Long]].get(0) == checkpoint,
       |      OUTPUTS(0).R4[Coll[Long]].get(3) == ticketCount,
       |      OUTPUTS(0).R4[Coll[Long]].get(4) == stakeCount
       |    )))
       |  } else {
       |    if(OUTPUTS(0).tokens(2)._2 == SELF.tokens(2)._2 + 1){
       |      // unlocking staking tokens
       |      // INPUTS: SELF(Config), Ticket, ReservedTokens --> OUTPUTS: Config, UnlockedStakingTokens
       |      sigmaProp(allOf(Coll(
       |        // config setting checking
       |        selfReplication,
       |        OUTPUTS(0).tokens(1)._2 == SELF.tokens(1)._2,
       |        OUTPUTS(0).R4[Coll[Long]].get(0) == checkpoint,
       |        OUTPUTS(0).R4[Coll[Long]].get(3) == ticketCount - 1,
       |        OUTPUTS(0).R4[Coll[Long]].get(4) == stakeCount - INPUTS(1).tokens(1)._2,
       |        // income recipient checking
       |        INPUTS(1).R4[Coll[Long]].get(0) < checkpoint,
       |        INPUTS(1).R4[Coll[Long]].get(1) == checkpoint
       |      )))
       |    } else {
       |      if(OUTPUTS(0).tokens(2)._2 == SELF.tokens(2)._2 - 1){
       |        // locking staking tokens
       |        // INPUTS: SELF(Config), StakingTokens --> OUTPUTS: Config, Ticket, ReservedToken
       |        sigmaProp(allOf(Coll(
       |          // config setting checking
       |          selfReplication,
       |          OUTPUTS(0).tokens(1)._2 == SELF.tokens(1)._2,
       |          OUTPUTS(0).R4[Coll[Long]].get(0) == checkpoint,
       |          OUTPUTS(0).R4[Coll[Long]].get(3) == ticketCount + 1,
       |          OUTPUTS(0).R4[Coll[Long]].get(4) == stakeCount + OUTPUTS(1).tokens(1)._2,
       |          // ticket setting
       |          blake2b256(OUTPUTS(1).propositionBytes) == ticketHash,
       |          OUTPUTS(1).tokens(0)._1 == SELF.tokens(2)._1,
       |          OUTPUTS(1).tokens(1)._1 == stakingToken,
       |          OUTPUTS(1).R4[Coll[Long]].get(0) == checkpoint,
       |          OUTPUTS(1).R4[Coll[Long]].get(1) == checkpoint,
       |          OUTPUTS(1).R4[Coll[Long]].get(2) == fee,
       |          OUTPUTS(1).R4[Coll[Long]].get(3) == minBoxVal,
       |          OUTPUTS(1).R6[Coll[Byte]].get == SELF.id,
       |          OUTPUTS(1).value >= minTicketValue,
       |          // reserved token issue
       |          OUTPUTS(2).tokens(0)._1 == SELF.id
       |        )))
       |      } else {
       |        if(OUTPUTS(0).tokens(1)._2 == SELF.tokens(1)._2 - 1){
       |          // distribution creation
       |          // INPUTS: SELF(Config), Income --> OUTPUTS: Config, Distribution, Income[Optional]
       |          val ergShare: Long = OUTPUTS(1).R4[Coll[Long]].get(1)
       |          val distValue = (ergShare * stakeCount) + fee
       |          val ergDistribute = allOf(Coll(
       |            // config setting checking
       |            selfReplication,
       |            OUTPUTS(0).tokens(2)._2 == SELF.tokens(2)._2,
       |            OUTPUTS(0).R4[Coll[Long]].get(0) == checkpoint + 1,
       |            OUTPUTS(0).R4[Coll[Long]].get(3) == ticketCount,
       |            OUTPUTS(0).R4[Coll[Long]].get(4) == stakeCount,
       |            // distribution box checking
       |            blake2b256(OUTPUTS(1).propositionBytes) == distributionHash,
       |            OUTPUTS(1).tokens(0)._1 == SELF.tokens(1)._1,
       |            OUTPUTS(1).value == distValue,
       |            OUTPUTS(1).R4[Coll[Long]].get(0) == checkpoint,
       |            OUTPUTS(1).R4[Coll[Long]].get(3) == fee,
       |            OUTPUTS(1).R5[Long].get == ticketCount
       |          ))
       |
       |          if(INPUTS(1).tokens.size == 0) {
       |            sigmaProp(ergDistribute && ergShare >= minErgShare)
       |          } else {
       |            val tokenShare = OUTPUTS(1).R4[Coll[Long]].get(2)
       |            val tokenCount = tokenShare * stakeCount
       |            sigmaProp(allOf(Coll(
       |              ergDistribute,
       |              // token distribute setting
       |              tokenShare >= minTokenShare,
       |              OUTPUTS(1).tokens(1)._2 == tokenCount
       |            )))
       |          }
       |        } else {sigmaProp(false)}
       |      }
       |    }
       |  }
       |}""".stripMargin

}
