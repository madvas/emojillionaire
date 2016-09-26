contract Dice is usingOraclize {

    uint public pwin = 5000; //probability of winning (10000 = 100%)
    uint public edge = 200; //edge percentage (10000 = 100%)
    uint public maxWin = 100; //max win (before edge is taken) as percentage of bankroll (10000 = 100%)
    uint public minBet = 1 finney;
    uint public maxInvestors = 5; //maximum number of investors
    uint public ownerEdge = 50; //edge percentage (10000 = 100%)
    uint public divestFee = 50; //divest fee percentage (10000 = 100%)

    uint constant safeGas = 25000;
    uint constant oraclizeGasLimit = 150000;

    struct Investor {
        address user;
        uint capital;
    }
    mapping(uint => Investor) investors; //starts at 1
    uint public numInvestors = 0;
    mapping(address => uint) investorIDs;
    uint public invested = 0;

    address owner;
    bool public isStopped;

    struct Bet {
        address user;
        uint bet; // amount
        uint roll; // result
	    uint fee;
    }
    mapping (bytes32 => Bet) bets;
    bytes32[] betsKeys;
    uint public amountWagered = 0;
    int public profit = 0;
    int public takenProfit = 0;
    int public ownerProfit = 0;

    function Dice(uint pwinInitial, uint edgeInitial, uint maxWinInitial, uint minBetInitial, uint maxInvestorsInitial, uint ownerEdgeInitial, uint divestFeeInitial) {

        oraclize_setProof(proofType_TLSNotary | proofStorage_IPFS);

        pwin = pwinInitial;
        edge = edgeInitial;
        maxWin = maxWinInitial;
        minBet = minBetInitial;
        maxInvestors = maxInvestorsInitial;
        ownerEdge = ownerEdgeInitial;
        divestFee = divestFeeInitial;

        owner = msg.sender;
    }


    function() {
        bet();
    }

    function bet() {
        if (isStopped) throw;
        uint oraclizeFee = OraclizeI(OAR.getAddress()).getPrice("URL", oraclizeGasLimit);
        if (msg.value < oraclizeFee) throw;
        uint betValue = msg.value - oraclizeFee;
        if ((((betValue * ((10000 - edge) - pwin)) / pwin ) <= (maxWin * getBankroll()) / 10000) && (betValue >= minBet)) {
            bytes32 myid = oraclize_query("URL", "json(https://api.random.org/json-rpc/1/invoke).result.random.data.0", 'BDXJhrVpBJ53o2CxlJRlQtZJKZqLYt5IQe+73YDS4HtNjS5HodbIB3tvfow7UquyAk085VkLnL9EpKgwqWQz7ZLdGvsQlRd2sKxIolNg9DbnfPspGqLhLbbYSVnN8CwvsjpAXcSSo3c+4cNwC90yF4oNibkvD3ytapoZ7goTSyoUYTfwSjnw3ti+HJVH7N3+c0iwOCqZjDdsGQUcX3m3S/IHWbOOQQ5osO4Lbj3Gg0x1UdNtfUzYCFY79nzYgWIQEFCuRBI0n6NBvBQW727+OsDRY0J/9/gjt8ucibHWic0=', oraclizeGasLimit); // encrypted arg: '\n{"jsonrpc":2.0,"method":"generateSignedIntegers","params":{"apiKey":"YOUR_API_KEY","n":1,"min":1,"max":10000},"id":1}'
            bets[myid] = Bet(msg.sender, betValue, 0, oraclizeFee);
            betsKeys.push(myid);
        } else {
            throw; // invalid bet size
        }
    }

    function numBets() constant returns(uint) {
        return betsKeys.length;
    }

    function minBetAmount() constant returns(uint) {
        uint oraclizeFee = OraclizeI(OAR.getAddress()).getPrice("URL", oraclizeGasLimit);
        return oraclizeFee+minBet;
    }

    function safeSend(address addr, uint value) internal {
        if (!(addr.call.gas(safeGas).value(value)())){
            ownerProfit += int(value);
        }
    }

    function __callback(bytes32 myid, string result, bytes proof) {
        if (msg.sender != oraclize_cbAddress()) throw;

        Bet thisBet = bets[myid];
        if (thisBet.bet>0) {
            if ((isStopped == false)&&(((thisBet.bet * ((10000 - edge) - pwin)) / pwin ) <= maxWin * getBankroll() / 10000)) {
                uint roll = parseInt(result);
                if (roll<1 || roll>10000){
                    safeSend(thisBet.user, thisBet.bet);
                    return;
                }

                bets[myid].roll = roll;

                int profitDiff;
                if (roll-1 < pwin) { //win
                    uint winAmount = (thisBet.bet * (10000 - edge)) / pwin;
                    safeSend(thisBet.user, winAmount);
                    profitDiff = int(thisBet.bet - winAmount);
                } else { //lose
                    safeSend(thisBet.user, 1);
                    profitDiff = int(thisBet.bet) - 1;
                }

                ownerProfit += (profitDiff*int(ownerEdge))/10000;
                profit += profitDiff-(profitDiff*int(ownerEdge))/10000;

                amountWagered += thisBet.bet;
            } else {
                //bet is too big (bankroll may have changed since the bet was made)
                safeSend(thisBet.user, thisBet.bet);
            }
        }
    }

    function getBet(uint id) constant returns(address, uint, uint, uint) {
        if(id<betsKeys.length)
        {
            bytes32 betKey = betsKeys[id];
            return (bets[betKey].user, bets[betKey].bet, bets[betKey].roll, bets[betKey].fee);
        }
    }

    function invest() {
        if (isStopped) throw;

        if (investorIDs[msg.sender]>0) {
            rebalance();
            investors[investorIDs[msg.sender]].capital += msg.value;
            invested += msg.value;
        } else {
            rebalance();
            uint investorID = 0;
            if (numInvestors<maxInvestors) {
                investorID = ++numInvestors;
            } else {
                for (uint i=1; i<=numInvestors; i++) {
                    if (investors[i].capital<msg.value && (investorID==0 || investors[i].capital<investors[investorID].capital)) {
                        investorID = i;
                    }
                }
            }
            if (investorID>0) {
                if (investors[investorID].capital>0) {
                    divest(investors[investorID].user, investors[investorID].capital);
                    investorIDs[investors[investorID].user] = 0;
                }
                if (investors[investorID].capital == 0 && investorIDs[investors[investorID].user] == 0) {
                    investors[investorID].user = msg.sender;
                    investors[investorID].capital = msg.value;
                    invested += msg.value;
                    investorIDs[msg.sender] = investorID;
                } else {
                    throw;
                }
            } else {
                throw;
            }
        }
    }

    function rebalance() private {
        if (takenProfit != profit) {
            uint newInvested = 0;
            uint initialBankroll = getBankroll();
            for (uint i=1; i<=numInvestors; i++) {
                investors[i].capital = getBalance(investors[i].user);
                newInvested += investors[i].capital;
            }
            invested = newInvested;
            if (newInvested != initialBankroll && numInvestors>0) {
                ownerProfit += int(initialBankroll - newInvested); //give the rounding error to the first investor
                invested += (initialBankroll - newInvested);
            }
            takenProfit = profit;
        }
    }

    function divest(address user, uint amount) private {
        if (investorIDs[user]>0) {
            rebalance();
            if (amount>getBalance(user)) {
                amount = getBalance(user);
            }
            investors[investorIDs[user]].capital -= amount;
            invested -= amount;

            uint newAmount = (amount*divestFee)/10000; // take a fee from the deinvest amount
            ownerProfit += int(newAmount);
            safeSend(user, (amount-newAmount));
        }
    }

    function divest(uint amount) {
        if (msg.value>0) throw;
        divest(msg.sender, amount);
    }

    function divest() {
        if (msg.value>0) throw;
        divest(msg.sender, getBalance(msg.sender));
    }

    function getBalance(address user) constant returns(uint) {
        if (investorIDs[user]>0 && invested>0) {
            return investors[investorIDs[user]].capital * getBankroll() / invested;
        } else {
            return 0;
        }
    }

    function getBankroll() constant returns(uint) {
        uint bankroll = uint(int(invested)+profit+ownerProfit-takenProfit);
        if (this.balance < bankroll){
            log0("bankroll_mismatch");
            bankroll = this.balance;
        }
        return bankroll;
    }

    function getMinInvestment() constant returns(uint) {
        if (numInvestors<maxInvestors) {
            return 0;
        } else {
            uint investorID;
            for (uint i=1; i<=numInvestors; i++) {
                if (investorID==0 || getBalance(investors[i].user)<getBalance(investors[investorID].user)) {
                    investorID = i;
                }
            }
            return getBalance(investors[investorID].user);
        }
    }

    function getStatus() constant returns(uint, uint, uint, uint, uint, uint, int, uint, uint) {
        return (getBankroll(), pwin, edge, maxWin, minBet, amountWagered, profit, getMinInvestment(), betsKeys.length);
    }

    function stopContract() {
        if (owner != msg.sender) throw;
        isStopped = true;
    }

    function resumeContract() {
        if (owner != msg.sender) throw;
        isStopped = false;
    }

    function forceDivestAll() {
        forceDivestAll(false);
    }

    function forceDivestAll(bool ownerTakeChangeAndProfit) {
        if (owner != msg.sender) throw;
        for (uint investorID=1; investorID<=numInvestors; investorID++) {
            divest(investors[investorID].user, getBalance(investors[investorID].user));
        }
        if (ownerTakeChangeAndProfit) owner.send(this.balance);
    }

    function ownerTakeProfit() {
        ownerTakeProfit(false);
    }

    function ownerTakeProfit(bool takeChange) {
        if (owner != msg.sender) throw;
        if (takeChange){
            uint investorsCapital = 0;
            for (uint i=1; i<=numInvestors; i++) {
                investorsCapital += investors[i].capital;
            }
            if ((investorsCapital == 0)&&(this.balance != uint(ownerProfit))){
                owner.send(this.balance);
                ownerProfit = 0;
            }
        } else {
            owner.send(uint(ownerProfit));
            ownerProfit = 0;
        }
    }

}