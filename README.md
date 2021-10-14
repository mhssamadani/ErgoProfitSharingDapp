## Components

Our solution consists of some components that we briefly describe here, and then we explain their relationships and detailed scenarios. We tried to use the "*x box*" phrase when there is a unique box in the network with the *x* contract and the "*y contract*" when several boxes are guarded with the *y* contract.

1. **Distribution Contract**: A contract that is responsible for reward distribution. When income hits a threshold, it receives the income and distributes it among the stakeholders. The box guarded with this contract can only receive one token type.
3. **Staking Contract**: A contract representing the staking amount belonging to one of the stakeholders. Each stakeholder receives income based on the number of *Staking Tokens* locked in the Staking Contract. The Staking Contract keeps track of the received income to make sure no one gets income twice.
4. **Staking Token**: A unique token that represents the staking proportions. They are locked up in a contract that ensures this token can not be burnt out when they receive stakes, even intentionally.
5. **Config Box**: A box that stores the staking setting, i.e., the income checkpoint and the number of current active tokens. It locks up the *Staking Token* within the *Staking Contract* in order to activate it. We call the locked staking token a **Ticket** afterward.
6. **Locker Token**: Tickets are *Staking Contracts* which hold the *Staking Tokens*. Creating a *Staking Contract* should be limited to the *Config Box* cause it's responsible for tracking the staking settings. Then we require a special token that represents the valid tickets made by the config box. The config box poses plenty of *Locker Tokens* and uses one for each locking.
7. **Distribution token**: Each distribution contract has a unique number called a checkpoint, representing the income order. It is also used to make sure each Ticket only gets the income once. The distribution box creation must be limited to the config box, as it should keep track of the checkpoint. Then each valid *Distribution Contracts* created by the config box has a *Distribution Token*.
8. **Reserved Token**: After locking the staking tokens by the config box. an NFT is created that belongs to the stakeholder. When the stakeholder wants to unlock his tokens, they should redeem the reserved token and get back its staking tokens.
9. **Income Contract**: In some cases, the service income is not cleanly available at the first moment. Then we need a contract that collects the service income. Then as it hits the income threshold, the income will be distributed among the stakeholders. With this design, the stakeholders can ensure that the contract only spends the service income on paying their stakes.


## Requirements

### Config Box
The Config box stores three values the **number of active tokens in the next epoch**, the **income checkpoint**, and the **income threshold**. Config Box is responsible for ticket creation and burning so it can track the active tokens. It uses one of the *locker Tokens* here to create a Ticket. The created Ticket can receive the income from the next checkpoint. The checkpoint is actually a number that increases each time an income distribution happens. We will discuss the distribution later.
<p align="center">
    <img src=Images/LockingTx.png>
</p>

System income can be distributed if it hits the threshold. Since the distribution process imposes fees, we don't want that threshold to be that small. However, on the other side, we want to split the system income as soon as possible. Thus we should choose a reasonable threshold here. Then we chose this scenario for the income threshold:

- **Fixed Portion**: As we want to split the income between the active tokens, we can set this threshold to be the portion each stakeholder receives. So that the income amount must be greater than the `#active_tokens * portion`.

As the system asset can be Erg or different token types, the threshold can be set separately for each one. For example, the distribution happens when each stakeholder receives 10 Erg or 100 other token types.


### Distribution Contract
The Distribution Contract is responsible for distributing the system income between the stakeholders. It stores the **income checkpoint** and **staking portion** and receives the income along with the distribution setting in the funding transaction. This contract is distinguishable by its distribution token received from the config box. The Config Box checkpoint is incremented by one in this transaction, but other settings are protected.
<p align="center">
    <img src=Images/FundingTx.png>
</p>

The received income consists of Erg and a token type (The Distribution Contract can distribute only one token type rather than the Erg). The number of the Ergs and tokens is dividable by the current active tokens of the system. We describe the distribution transaction later.

After the distribution is finished, then an empty distribution box remains in the network. At its final stage, it redeems the distribution token to the config box and disappears completely.

### Staking Contract or Ticket
The staking token is locked up in a contract named staking contract. The staking contract specifies three primary parameters: **staking proportion**(number of tokens), **recipient address**, and **reward receipt checkpoint**.

The contract guarantees:

- Tokens won't burn in any situation.
- The reward is sent to the receipt address.
- The recipient won't be rewarded unless the reward checkpoint is updated (incremented by one)
- No one receives the reward more than once

The contract is designed such a way that it can be spent in two situations:

1. **Receiving the epoch reward**, The Ticket is responsible for the distribution tx fee. As each stakeholder is in charge of his transactions, he should charge his Ticket to pay the income transaction fees. Anyone can spend the box guarded with this contract if:
    - it is spent along with the Distribution Contract.
    - it sends the reward to the specified recipient address.
    - the same contract with the same parameters(except checkpoint) is in output boxes.
    - the checkpoint is equal to the Distribution Contract checkpoint, and it is incremented afterward.

<p align="center">
    <img src = Images/DistributionTx.png>
</p>

2. **Token unlocking**, the stakeholder can transfer his asset to anyone else. In order to transfer the tokens, he needs to unlock them by spending the Ticket along with the Config Box. The Config Box takes back the Locker Token in this step and updates the number of active tokens. As the Config Box is the bottleneck of this system, to avoid the DoS attacks by users, each user can only unlock its tokens when it receives at least one reward round (It’s a setting and can be customized based on the usage situation).
<p align="center">
    <img src = Images/UnlockingTx.png>
</p>

### Income Contract

All service incomes are going to a special contract named *Income*. Since the service income may not hit the distribution threshold each time, we need a mechanism to merge some of them and create bigger income boxes. So an income box can be spent in two transactions:

1. **Merge Transaction**: Incomes can be merged in this transaction. A batch of income boxes is merged to create a bigger box with all spending boxes assets. The batch size has minimum and maximum constraints to minimize the fees imposed by this transaction.
<p align="center">
    <img src = Images/MergeTx.png>
</p>

2. **Funding Transaction**: As we saw earlier, system income is used as the fund of the distribution creation. This contract specifically makes sure that the excessive amount comes back to the same contract. Anyway, the funding transaction can also be funded in any other ways rather than the income contract.


