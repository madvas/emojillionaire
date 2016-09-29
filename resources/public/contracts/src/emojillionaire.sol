pragma solidity ^0.4.1;

import "oraclizeAPI.sol";
import "strings.sol";

contract Emojillionaire is usingOraclize {
    using strings for *;

    bool constant privateNet = false;

    struct Bet {
        address playerAddress;
        uint16[] guesses;
        uint16[] rolls;
        uint guessesCost;
        uint betFee;
        uint oraclizeFee;
        uint jackpotKey;
        bytes32 queryId;
        uint date;
    }

    struct Sponsorship {
        address sponsorAddress;
        string name;
        uint amount;
        uint fee;
        uint date;
        uint jackpotKey;
    }

    struct Sponsor {
        string name;
        uint amount;
        bool exists;
    }

    struct Jackpot {
        uint amount;
        uint since;
    }

    struct Player {
        uint credit;
        bool exists;
    }

    Jackpot[] public jackpots;
    Bet[] public bets;

    mapping (address => Player) public players;
    address[] public playerKeys;

    mapping (address => Sponsor) public sponsors;
    address[] public sponsorKeys;

    Sponsorship[] public sponsorships;

    address[] public topSponsorsAddresses;

    enum States {active, inactive} States public state;

    address public developer;
    uint16 public totalPossibilities;
    uint public guessCost;
    uint16 public guessFeeRatio;
    uint public guessFee;
    uint16 public maxGuessesAtOnce;
    uint16 public sponsorNameMaxLength;
    uint16 public sponsorshipFeeRatio;
    uint public sponsorshipMinAmount;
    uint16 public topSponsorsMaxLength;

    uint public totalJackpotAmountsWon;
    uint public totalGuessesCount;
    uint public totalSponsorshipsAmount;

    uint public oraclizeGasLimit;

    event onPlayerCreditChange(address playerAddress, uint credit);
    event onJackpotAmountChange(uint indexed jackpotKey, uint amount);
    event onNewJackpotStarted(uint date, uint jackpotKey);
    event onJackpotWon(address indexed playerAddress, uint roll, uint date, uint amount, uint jackpotKey,
                       uint totalJackpotAmountsWon);
    event onRolled(address indexed playerAddress, uint16[] guesses, uint16[] rolls, uint date, uint betKey);
    event onNewPlayer(address playerAddress, uint date, uint playersCount);
    event onStateChange(States state);
    event onSettingsChange(uint guessCost, uint16 totalPossibilities, uint16 guessFeeRatio, uint guessFee,
                           uint16 maxGuessesAtOnce, uint16 sponsorNameMaxLength, uint16 sponsorshipFeeRatio,
                           uint sponsorshipMinAmount);
    event onSponsorshipAdded(uint indexed jackpotKey, address indexed sponsorAddress, string name, uint amount,
                             uint fee, uint date, uint sponsorsCount, uint sponsorshipsCount,
                             uint totalSponsorshipsAmount);
    event onSponsorUpdated(address indexed sponsorAddress, string name, uint amount, uint date);
    event onTopSponsorAdded(address indexed sponsorAddress, string name, uint amount, uint date);
    event onTopSponsorRemoved(address indexed sponsorAddress);

    event onBet(uint betsCount, uint totalGuessesCount);
    event onOraclizeFeeChange(uint oraclizeFee);

    modifier onlyDeveloper() {
        if (msg.sender != developer) throw;
        _;
    }

    modifier onlyActive {
    if (state != States.active)
        throw;
    _;
    }

    function Emojillionaire() {
        oraclize_setNetwork(networkID_testnet);
        developer = msg.sender;
        state = States.active;

        totalPossibilities = 333;
        guessCost = 0.1 ether;
        guessFeeRatio = 100; // This means 1%
        guessFee = calculateGuessFee(guessFeeRatio, guessCost);

//        oraclizeGasLimit = 700000;
//        maxGuessesAtOnce = 20;
//        sponsorNameMaxLength = 30;
//        sponsorshipFeeRatio = 500; // 5%
//        sponsorshipMinAmount = 1 ether;
//        topSponsorsMaxLength = 10;
        startNewJackpot(0);
    }

    function calculateGuessFee(uint16 ratio, uint betVal) constant returns(uint) {
        return betVal / 10000 * ratio;
    }

    function startNewJackpot(uint amount) private {
        jackpots.push(Jackpot(amount, now));
        onNewJackpotStarted(now, jackpots.length - 1);
        onJackpotAmountChange(currentJackpotKey(), amount);
    }

    function currentJackpotKey() constant returns(uint) {
        return jackpots.length - 1;
    }

    function changeDeveloper(address _developer)
        onlyDeveloper {
            if (_developer == 0x0) throw;
            developer = _developer;
    }

    function setInactiveState()
        onlyDeveloper {
          state = States.inactive;
          onStateChange(state);
    }

    function setActiveState()
        onlyDeveloper {
          state = States.active;
          onStateChange(state);
    }

    function refundEveryone()
        onlyDeveloper {
            for (uint i = 0; i < playerKeys.length; i++) {
                address playerAddress = playerKeys[i];
                uint credit = players[playerAddress].credit;
                if (credit > 0) {
                    if (!playerAddress.send(credit)) throw;
                    changeCredit(playerAddress, 0, false);
                }
            }

            for (i = 0; i < bets.length; i++) {
                Bet bet = bets[i];
                if (!bet.playerAddress.send(bet.guessesCost)) throw;
            }

            for (i = 0; i < sponsorships.length; i++) {
                Sponsorship sponsorship = sponsorships[i];
                uint jackpotKey = currentJackpotKey();
                if (sponsorship.jackpotKey == jackpotKey) {
                    if (!sponsorship.sponsorAddress.send(sponsorship.amount)) throw;
                }
            }

            setJackpotAmount(0);
    }


    function sortTopSponsors() {
        uint n = topSponsorsAddresses.length;

        for (uint c = 0 ; c < ( n - 1 ); c++) {
            for (uint d = 0 ; d < n - c - 1; d++) {
                if (sponsors[topSponsorsAddresses[d]].amount < sponsors[topSponsorsAddresses[d+1]].amount) {
                    (topSponsorsAddresses[d], topSponsorsAddresses[d+1]) =
                        (topSponsorsAddresses[d + 1], topSponsorsAddresses[d]);
                }
            }
        }
    }

    function changeSettings(uint _guessCost, uint16 _totalPossibilities, uint16 _guessFeeRatio,
                            uint16 _maxGuessesAtOnce, uint16 _sponsorNameMaxLength, uint16 _sponsorshipFeeRatio,
                            uint _sponsorshipMinAmount)
        onlyDeveloper {
            if (_guessFeeRatio < 0 || _guessFeeRatio > guessFeeRatio) throw;
            if (_guessCost < guessCost) throw;
            if (_totalPossibilities < totalPossibilities) throw;
            if (_sponsorNameMaxLength < 0) throw;
            if (_sponsorshipFeeRatio < 0) throw;
            if (_sponsorshipMinAmount < 0) throw;

            totalPossibilities = _totalPossibilities;
            guessCost = _guessCost;
            guessFeeRatio = _guessFeeRatio;
            guessFee = calculateGuessFee(guessFeeRatio, guessCost);
            maxGuessesAtOnce = _maxGuessesAtOnce;
            sponsorNameMaxLength = _sponsorNameMaxLength;
            sponsorshipFeeRatio = _sponsorshipFeeRatio;
            sponsorshipMinAmount = _sponsorshipMinAmount;
            onSettingsChange(guessCost, totalPossibilities, guessFeeRatio, guessFee, maxGuessesAtOnce,
                             sponsorNameMaxLength, sponsorshipFeeRatio, sponsorshipMinAmount);
    }

    function setTopSponsorsMaxLength(uint16 _topSponsorsMaxLength)
        onlyDeveloper {
            if (_topSponsorsMaxLength < 0) throw;
            topSponsorsMaxLength = _topSponsorsMaxLength;
    }

    function setOraclizeGasLimit(uint _oraclizeGasLimit)
        onlyDeveloper {
            if (_oraclizeGasLimit < 0) throw;
            oraclizeGasLimit = _oraclizeGasLimit;
            onOraclizeFeeChange(getOraclizeFee());
    }

    function getSettings() constant returns (uint, uint16, uint16, uint, uint16, uint16, uint16, uint, uint) {
        return (guessCost, totalPossibilities, guessFeeRatio, guessFee, maxGuessesAtOnce, sponsorNameMaxLength,
                sponsorshipFeeRatio, sponsorshipMinAmount, getOraclizeFee());
    }

    function getInitialLoadData() constant returns (States, uint, uint, uint, uint, uint, uint, uint, uint, uint, uint,
                                                    address[]) {
        Jackpot jackpot = jackpots[currentJackpotKey()];
        return (state, jackpot.amount, jackpot.since, totalJackpotAmountsWon, jackpotsCount(), betsCount(),
                totalGuessesCount, playersCount(), sponsorsCount(), sponsorshipsCount(), totalSponsorshipsAmount,
                topSponsorsAddresses);
    }

    function getRandomNumber() private returns(uint) { // Used for development only
        return uint(uint256(block.blockhash(block.number - 1)) % totalPossibilities);
    }

    function validGuesses(uint16[] guesses) constant returns(bool) {
        bool valid = true;
        for (uint i = 0; i < guesses.length; i++) {
            if (!isValidBetInput(guesses[i])) {
                valid = false;
                break;
            }
        }
        return valid;
    }

    function isValidBetInput(uint16 betInput) constant returns(bool) {
        return betInput > 0 && betInput <= totalPossibilities;
    }

    function getRandomOrgQuery(uint guessesLength) constant returns(string) {
            var parts = new strings.slice[](4);
            parts[0] = "https://www.random.org/integers/?num=".toSlice();
            parts[1] = strings.uintToString(guessesLength).toSlice();
            parts[2] = "&min=1&col=1&base=10&format=plain&rnd=new&max=".toSlice();
            parts[3] = strings.uintToString(totalPossibilities).toSlice();
            return "".toSlice().join(parts);
    }

    function getOraclizeFee() constant returns(uint) {
        if (privateNet) {
            return 0;
        }
        return OraclizeI(OAR.getAddress()).getPrice("URL", oraclizeGasLimit);
     }

    function callOraclizeQuery(uint guessesLength) returns(bytes32) {
        if (privateNet) {
            return strings.uintToBytes(getRandomNumber());
        }
        return oraclize_query("URL", getRandomOrgQuery(guessesLength), oraclizeGasLimit);
    }

    function getNumbersFromString(string s, string delimiter, uint16 howmany) constant returns(uint16[] numbers) {
         strings.slice memory myresult = s.toSlice();
         strings.slice memory delim = delimiter.toSlice();
         numbers = new uint16[](howmany);
         for(uint8 i = 0; i < howmany; i++){
             numbers[i] = uint16(parseInt(myresult.split(delim).toString()));
         }
         return numbers;
     }

    function bet(uint16[] guesses) 
        onlyActive
        payable {
            if (guesses.length < 1 || guesses.length > maxGuessesAtOnce) {
                throw;
            }

            if (!validGuesses(guesses)) throw;

            uint guessesCost = guessCost * guesses.length;
            uint betFee = guessFee * guesses.length;
            uint oraclizeFee = getOraclizeFee();
            uint totalCost = oraclizeFee + guessesCost + betFee;
            uint totalPlayerCredit = msg.value + players[msg.sender].credit;

            if (totalPlayerCredit < totalCost) {
                throw;
            }

            if (!players[msg.sender].exists) {
                playerKeys.push(msg.sender);
                players[msg.sender].exists = true;
                onNewPlayer(msg.sender, now, playersCount());
            }

            if (msg.value - totalCost > 0) {
                changeCredit(msg.sender, msg.value - totalCost, true);
            }

            setJackpotAmount(currentJackpotAmount() + guessesCost);

            bytes32 queryId = callOraclizeQuery(guesses.length);

            bets.push(Bet(msg.sender, guesses, new uint16[](0), guessesCost, betFee, oraclizeFee, currentJackpotKey(),
                          queryId, now));

            totalGuessesCount += guesses.length;
            onBet(betsCount(), totalGuessesCount);

            if (privateNet) {
                __callback(queryId, "200\n147\n138\n139\n102\n210\n333\n25\n305\n187\n272\n136\n48\n312\n34\n130\n101\n319\n266\n0");
            }
    }

    function sponsor(string name) 
        onlyActive
        payable {
            if (msg.value < sponsorshipMinAmount) throw;
            if (name.toSlice().len() > sponsorNameMaxLength) throw;
            uint fee = msg.value / (10000 / sponsorshipFeeRatio);
            uint amount = msg.value - fee;

            if (!sponsors[msg.sender].exists) {
                sponsorKeys.push(msg.sender);
            }

            sponsorships.push(Sponsorship(msg.sender, name, amount, fee, now, currentJackpotKey()));
            sponsors[msg.sender].exists = true;
            sponsors[msg.sender].amount += amount;
            sponsors[msg.sender].name = name;
            totalSponsorshipsAmount += amount;
            setJackpotAmount(currentJackpotAmount() + amount);

            if (!hasAddress(topSponsorsAddresses, msg.sender) && lastTopSponsorAmount() < sponsors[msg.sender].amount) {
                topSponsorsAddresses.push(msg.sender);
                onTopSponsorAdded(msg.sender, name, sponsors[msg.sender].amount, now);
            }

            sortTopSponsors();

            if (topSponsorsAddresses.length > topSponsorsMaxLength) {
                onTopSponsorRemoved(topSponsorsAddresses[topSponsorsAddresses.length - 1]);
                delete topSponsorsAddresses[topSponsorsAddresses.length - 1];
            }

            onSponsorUpdated(msg.sender, name, sponsors[msg.sender].amount, now);
            onSponsorshipAdded(currentJackpotKey(), msg.sender, name, amount, fee, now, sponsorsCount(),
                               sponsorshipsCount(), totalSponsorshipsAmount);
    }

    function jackpotsCount() constant returns(uint) {
        return jackpots.length;
    }

    function betsCount() constant returns(uint) {
        return bets.length;
    }

    function playersCount() constant returns(uint) {
        return playerKeys.length;
    }

    function currentJackpotAmount() constant returns(uint) {
        return jackpots[currentJackpotKey()].amount;
    }

    function sponsorsCount() constant returns(uint) {
        return sponsorKeys.length;
    }

    function sponsorshipsCount() constant returns(uint) {
        return sponsorships.length;
    }

    function lastTopSponsorAmount() constant returns(uint) {
        uint topSponsorsCount = topSponsorsAddresses.length;
        if (topSponsorsCount == 0) {
            return 0;
        }
        sponsors[topSponsorsAddresses[topSponsorsCount - 1]].amount;
    }

    function getBet(uint betKey) constant returns(address, uint16[], uint16[], uint, uint, uint, uint, bytes32, uint) {
        if (betKey < bets.length) {
            Bet bet = bets[betKey];
            return (bet.playerAddress, bet.guesses, bet.rolls, bet.guessesCost, bet.betFee, bet.oraclizeFee, bet.jackpotKey,
                    bet.queryId, bet.date);
        }
    }

    function hasAddress(address[] arr, address addr) constant returns(bool) {
        bool hasAddr = false;
        for (uint i = 0; i < arr.length; i++) {
            if (arr[i] == addr) {
                hasAddr = true;
                break;
            }
        }
        return hasAddr;
    }

    function changeCredit(address playerAddress, uint amount, bool add) private {
        if (add) {
            players[playerAddress].credit += amount;
        } else {
            players[playerAddress].credit = amount;
        }

        onPlayerCreditChange(playerAddress, players[playerAddress].credit);
    }

    function setJackpotAmount(uint amount) private {
        if (amount < 0) throw;
        jackpots[currentJackpotKey()].amount = amount;
        onJackpotAmountChange(currentJackpotKey(), amount);
    }

    function checkWinning(Bet bet) private {
        for (uint i = 0; i < bet.guesses.length; i++) {
            if (bet.guesses[i] == bet.rolls[i]) {
                uint creditToTransfer = currentJackpotAmount() - guessCost; // Must leave at least some price for next jackpot
                changeCredit(bet.playerAddress, creditToTransfer, true);
                totalJackpotAmountsWon += creditToTransfer;
                onJackpotWon(bet.playerAddress, bet.rolls[i], now, creditToTransfer, currentJackpotKey(),
                             totalJackpotAmountsWon);
                startNewJackpot(guessCost);
                break;
            }
        }
    }


    function getBetKeyByQueryId(bytes32 queryId) returns(bool, uint) {
        uint foundKey;
        bool found = false;
        for (uint i = bets.length - 1; i >= 0 ; i--) {
            if (bets[i].queryId == queryId) {
                foundKey = i;
                found = true;
                break;
            }
        }
        return (found, foundKey);
    }

    function __callback(bytes32 queryId, string result)
        onlyActive {
            if (!privateNet && msg.sender != oraclize_cbAddress())
                throw;

            uint betKey;
            bool betKeyFound;
            (betKeyFound, betKey) = getBetKeyByQueryId(queryId);

            if (!betKeyFound) {
                throw;
            }

            Bet thisBet = bets[betKey];

            if (thisBet.rolls.length > 0) throw; // Callback already called

            var rolls = getNumbersFromString(result,"\n", uint16(thisBet.guesses.length));
            validGuesses(rolls);

            thisBet.rolls = rolls;
            onRolled(thisBet.playerAddress, thisBet.guesses, rolls, now, betKey);

            if (validGuesses(rolls)) {
                checkWinning(thisBet);
            } else { // We got invalid response from random.org, so we refund player
                changeCredit(thisBet.playerAddress, thisBet.betFee + thisBet.guessesCost, true);
                setJackpotAmount(currentJackpotAmount() - thisBet.guessesCost);
            }
    }

    function withdraw()
        onlyActive {
            uint credit = players[msg.sender].credit;
            if (credit > 0) {
                changeCredit(msg.sender, 0, false);
                if (!msg.sender.send(credit)) throw;
            }
    }

    function developerWithdrawProfit()
        onlyDeveloper {
            if (!developer.send(this.balance - currentJackpotAmount())) throw;
    }

    function() {
        throw;
    }
}