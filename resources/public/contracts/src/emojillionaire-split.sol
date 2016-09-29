pragma solidity ^0.4.1;

import "oraclizeAPI.sol";
import "strings.sol";

contract EmojillionaireUtils {
    using strings for *;

    // parseInt
        function parseInt(string _a) internal returns (uint) {
            return parseInt(_a, 0);
        }

        // parseInt(parseFloat*10^_b)
        function parseInt(string _a, uint _b) internal returns (uint) {
            bytes memory bresult = bytes(_a);
            uint mint = 0;
            bool decimals = false;
            for (uint i=0; i<bresult.length; i++){
                if ((bresult[i] >= 48)&&(bresult[i] <= 57)){
                    if (decimals){
                       if (_b == 0) break;
                        else _b--;
                    }
                    mint *= 10;
                    mint += uint(bresult[i]) - 48;
                } else if (bresult[i] == 46) decimals = true;
            }
            if (_b > 0) mint *= 10**_b;
            return mint;
        }

    function getRandomOrgQuery(uint totalPossibilities, uint guessesLength) returns(string) {
        var parts = new strings.slice[](4);
        parts[0] = "https://www.random.org/integers/?num=".toSlice();
        parts[1] = strings.uintToString(guessesLength).toSlice();
        parts[2] = "&min=0&col=1&base=10&format=plain&rnd=new&max=".toSlice();
        parts[3] = strings.uintToString(totalPossibilities - 1).toSlice();
        return "".toSlice().join(parts);
    }

    function getNumbersFromString(string s, string delimiter, uint16 howmany) constant returns(uint16[] numbers){
         strings.slice memory myresult = s.toSlice();
         strings.slice memory delim = delimiter.toSlice();
         numbers = new uint16[](howmany);
         for(uint8 i = 0; i < howmany; i++){
             numbers[i] = uint16(parseInt(myresult.split(delim).toString()));
         }
         return numbers;
    }

    function strLen(string str) constant returns(uint){
        return str.toSlice().len();
    }

    function calculateGuessFee(uint16 ratio, uint betVal) constant returns(uint) {
        return betVal / 10000 * ratio;
    }

    function isValidBetInput(uint16 betInput, uint totalPossibilities) constant returns(bool) {
        return betInput >= 0 && betInput < totalPossibilities;
    }

    function validateGuesses(uint16[] guesses, uint totalPossibilities) constant returns(bool) {
        bool valid = true;
        for (uint i = 0; i < guesses.length; i++) {
            if (!isValidBetInput(guesses[i], totalPossibilities)) {
                valid = false;
                break;
            }
        }
        return valid;
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
}


contract EmojillionaireDb {
    struct Bet {
        address playerAddress;
        uint16[] guesses;
        uint16[] rolls;
        uint guessesCost;
        uint betFee;
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

    address public emojillionaireAddress;
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

     modifier onlyEmojillionaire() {
        if (msg.sender != emojillionaireAddress) throw;
        _;
     }

     EmojillionaireUtils emoUtils;

     function Emojillionaire(address emoUtilsAddress) {
             emoUtils = EmojillionaireUtils(emoUtilsAddress);
     }

     function setInactiveState()
         onlyEmojillionaire {
           state = States.inactive;
           onStateChange(state);
     }

     function setActiveState()
         onlyEmojillionaire {
           state = States.active;
           onStateChange(state);
     }

     function isActive() constant returns(bool) {
        return state == States.active;
     }

    function startNewJackpot(uint amount)
    onlyEmojillionaire {
        jackpots.push(Jackpot(amount, now));
        onNewJackpotStarted(now, jackpots.length - 1);
        onJackpotAmountChange(currentJackpotKey(), amount);
    }

    function currentJackpotKey() constant returns(uint) {
        return jackpots.length - 1;
    }

    function getCurrentJackpot() constant returns(uint, uint) {
        Jackpot currJackpot = jackpots[currentJackpotKey()];
        return (currJackpot.amount, currJackpot.since);
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
    onlyEmojillionaire {
            if (_guessFeeRatio < 0 || _guessFeeRatio > guessFeeRatio) throw;
            if (_guessCost < guessCost) throw;
            if (_totalPossibilities < totalPossibilities) throw;
            if (_sponsorNameMaxLength < 0) throw;
            if (_sponsorshipFeeRatio < 0) throw;
            if (_sponsorshipMinAmount < 0) throw;

            totalPossibilities = _totalPossibilities;
            guessCost = _guessCost;
            guessFeeRatio = _guessFeeRatio;
            guessFee = emoUtils.calculateGuessFee(guessFeeRatio, guessCost);
            maxGuessesAtOnce = _maxGuessesAtOnce;
            sponsorNameMaxLength = _sponsorNameMaxLength;
            sponsorshipFeeRatio = _sponsorshipFeeRatio;
            sponsorshipMinAmount = _sponsorshipMinAmount;
            onSettingsChange(guessCost, totalPossibilities, guessFeeRatio, guessFee, maxGuessesAtOnce,
                             sponsorNameMaxLength, sponsorshipFeeRatio, sponsorshipMinAmount);
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

    function getPlayerCredit(address playerAddress) constant returns(uint){
        return players[playerAddress].credit;
    }

    function getBetGuessesCount(uint betKey) constant returns(uint) {
        return bets[betKey].guesses.length;
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

    function changeCredit(address playerAddress, uint amount, bool add)
    onlyEmojillionaire {
        if (!players[playerAddress].exists) {
            playerKeys.push(playerAddress);
            onNewPlayer(playerAddress, now, playersCount());
        }

        players[playerAddress].exists = true;
        if (add) {
            players[playerAddress].credit += amount;
        } else {
            players[playerAddress].credit = amount;
        }

        onPlayerCreditChange(playerAddress, players[playerAddress].credit);
    }

    function setJackpotAmount(uint amount, bool add)
    onlyEmojillionaire {
        if (amount < 0) throw;
        uint currJackpotKey = currentJackpotKey();
        if (add) {
            jackpots[currJackpotKey].amount += amount;
        } else {
            jackpots[currJackpotKey].amount = amount;
        }

        onJackpotAmountChange(currJackpotKey, amount);
    }

    function addBet(address playerAddress, uint16[] guesses, uint guessesCost, uint betFee, bytes32 queryId)
    onlyEmojillionaire {
        bets.push(Bet(msg.sender, guesses, new uint16[](0), guessesCost, betFee, currentJackpotKey(),
                      queryId, now));

        totalGuessesCount += guesses.length;
        onBet(betsCount(), totalGuessesCount);
    }

    function addSposor(address sponsorAddress, uint sentAmount, string sponsorName)
    onlyEmojillionaire {
        if (sentAmount < sponsorshipMinAmount) throw;
        if (emoUtils.strLen(sponsorName) > sponsorNameMaxLength) throw;
        uint fee = sentAmount / (10000 / sponsorshipFeeRatio);
        uint amount = sentAmount - fee;

        if (!sponsors[sponsorAddress].exists) {
            sponsorKeys.push(sponsorAddress);
        }

        sponsorships.push(Sponsorship(sponsorAddress, sponsorName, amount, fee, now, currentJackpotKey()));
        sponsors[sponsorAddress].exists = true;
        sponsors[sponsorAddress].amount += amount;
        sponsors[sponsorAddress].name = sponsorName;
        totalSponsorshipsAmount += amount;
        setJackpotAmount(amount, true);

        if (!emoUtils.hasAddress(topSponsorsAddresses, sponsorAddress) &&
            lastTopSponsorAmount() < sponsors[sponsorAddress].amount) {

            topSponsorsAddresses.push(sponsorAddress);
            onTopSponsorAdded(sponsorAddress, sponsorName, sponsors[sponsorAddress].amount, now);
        }

        sortTopSponsors();

        if (topSponsorsAddresses.length > topSponsorsMaxLength) {
            onTopSponsorRemoved(topSponsorsAddresses[topSponsorsAddresses.length - 1]);
            delete topSponsorsAddresses[topSponsorsAddresses.length - 1];
        }

        onSponsorUpdated(sponsorAddress, sponsorName, sponsors[sponsorAddress].amount, now);
        onSponsorshipAdded(currentJackpotKey(), sponsorAddress, sponsorName, amount, fee, now, sponsorsCount(),
                           sponsorshipsCount(), totalSponsorshipsAmount);
    }

    function addRolls(uint betKey, uint16[] rolls)
    onlyEmojillionaire {
        Bet thisBet = bets[betKey];

        if (thisBet.rolls.length > 0) throw; // Callback already called

        emoUtils.validateGuesses(rolls, totalPossibilities);

        thisBet.rolls = rolls;
        onRolled(thisBet.playerAddress, thisBet.guesses, rolls, now, betKey);
        checkWinning(thisBet);
    }
}


contract Emojillionaire is usingOraclize {
    using strings for *;

    bool constant privateNet = false;
    address public developer;
    EmojillionaireDb public emoDb;
    EmojillionaireUtils public emoUtils;
    uint public oraclizeGasLimit;

    modifier onlyDeveloper() {
        if (msg.sender != developer) throw;
        _;
    }

    modifier onlyActive {
    if (emoDb.isActive())
        throw;
    _;
    }

    function Emojillionaire(address emoUtilsAddress, address emoDbAddress) {
        oraclize_setNetwork(networkID_testnet);
        developer = msg.sender;
//        state = States.active;
        emoUtils = EmojillionaireUtils(emoUtilsAddress);
        emoDb = EmojillionaireDb(emoDbAddress);

//        oraclizeGasLimit = 700000;
//        totalPossibilities = 333;
//        guessCost = 0.1 ether;
//        guessFeeRatio = 100; // This means 1%
//        guessFee = calculateGuessFee(guessFeeRatio, guessCost);
//
//        maxGuessesAtOnce = 20;
//        sponsorNameMaxLength = 30;
//        sponsorshipFeeRatio = 500; // 5%
//        sponsorshipMinAmount = 1 ether;
//        totalJackpotAmountsWon = 0;
//        totalGuessesCount = 0;
//        totalSponsorshipsAmount = 0;
//        topSponsorsMaxLength = 10;
        emoDb.startNewJackpot(0);
    }

    function changeDeveloper(address _developer)
        onlyDeveloper {
            if (_developer == 0x0) throw;
            developer = _developer;
    }

//    function refundEveryone()
//        onlyDeveloper {
//
//        uint playersCount = emoDb.playersCount();
//        for (uint i = 0; i < playersCount; i++) {
//            address playerAddress = emoDb.playerKeys(i);
//            uint credit = emoDb.players(playerAddress)(0);
//            if (credit > 0) {
//                if (!playerAddress.send(credit)) throw;
//                emoDb.changeCredit(playerAddress, 0, false);
//            }
//        }
//
//        uint16 betsCount = emoDb.betsCount();
//        for (i = 0; i < betsCount; i++) {
//            EmojillionaireDb.Bet bet = emoDb.bets[i];
//            if (!bet.playerAddress.send(bet.guessesCost)) throw;
//        }
//
//        uint16 sponsorsCount = emoDb.sponsorsCount();
//        for (i = 0; i < sponsorsCount; i++) {
//            EmojillionaireDb.Sponsorship sponsorship = emoDb.sponsorships[i];
//            uint jackpotKey = emoDb.currentJackpotKey();
//            if (sponsorship.jackpotKey == jackpotKey) {
//                if (!sponsorship.sponsorAddress.send(sponsorship.amount)) throw;
//            }
//        }
//
//        emoDb.setJackpotAmount(0);
//    }


//    function setTopSponsorsMaxLength(uint16 _topSponsorsMaxLength)
//    onlyDeveloper {
//        if (_topSponsorsMaxLength < 0) throw;
//        topSponsorsMaxLength = _topSponsorsMaxLength;
//    }
//
//    function setOraclizeGasLimit(uint _oraclizeGasLimit)
//        onlyDeveloper {
//            if (_oraclizeGasLimit < 0) throw;
//            oraclizeGasLimit = _oraclizeGasLimit;
//            emoDb.onOraclizeFeeChange(getOraclizeFee());
//    }

    function getSettings() constant returns (uint, uint16, uint16, uint, uint16, uint16, uint16, uint, uint) {
        return (emoDb.guessCost(), emoDb.totalPossibilities(), emoDb.guessFeeRatio(), emoDb.guessFee(),
                emoDb.maxGuessesAtOnce(),
                emoDb.sponsorNameMaxLength(), emoDb.sponsorshipFeeRatio(), emoDb.sponsorshipMinAmount(), getOraclizeFee());
    }

//    function getInitialLoadData() constant returns (EmojillionaireDb.States, uint, uint, uint, uint, uint, uint, uint, uint, uint, uint,
//                                                    address[]) {
//        uint jackpotAmount;
//        uint jackpotSince;
//        (jackpotAmount, jackpotSince) = emoDb.getCurrentJackpot();
//        return (emoDb.state(), jackpotAmount, jackpotSince, emoDb.totalJackpotAmountsWon(), emoDb.jackpotsCount(),
//                emoDb.betsCount(), emoDb.totalGuessesCount(), emoDb.playersCount(), emoDb.sponsorsCount(),
//                emoDb.sponsorshipsCount(), emoDb.totalSponsorshipsAmount(), emoDb.topSponsorsAddresses());
//    }

    function getOraclizeFee() constant returns(uint) {
        return OraclizeI(OAR.getAddress()).getPrice("URL", oraclizeGasLimit);
     }

    function callOraclizeQuery(uint guessesLength) returns(bytes32) {
//        if (privateNet) {
//            return strings.uintToBytes(getRandomNumber());
//        }

//        string a = emoUtils.getRandomOrgQuery(totalGuessesCount, guessesLength);
//        return oraclize_query("URL", a, oraclizeGasLimit);
    }

    function bet(uint16[] guesses) // 300k
    onlyActive
    payable
    {
        if (guesses.length < 1 || guesses.length > emoDb.maxGuessesAtOnce()) {
            throw;
        }

        if (!emoUtils.validateGuesses(guesses, emoDb.totalPossibilities())) throw;

        uint guessesCost = emoDb.guessCost() * guesses.length;
        uint betFee = emoDb.guessFee() * guesses.length;
        uint oraclizeFee = getOraclizeFee();
        uint totalCost = oraclizeFee + guessesCost + betFee;
        uint totalPlayerCredit = msg.value + emoDb.getPlayerCredit(msg.sender);

        if (totalPlayerCredit < totalCost) {
            throw;
        }

        if (msg.value - totalCost > 0) {
            emoDb.changeCredit(msg.sender, msg.value - totalCost, true);
        }

        emoDb.setJackpotAmount(guessesCost, true);

        bytes32 queryId = callOraclizeQuery(guesses.length);

        emoDb.addBet(msg.sender, guesses, guessesCost, betFee, queryId);
    }

    function sponsor(string name) // 600k
    onlyActive
    payable {
        emoDb.addSposor(msg.sender, msg.value, name);
    }

//    function getBet(uint betKey) constant returns(address, uint16[], uint16[], uint, uint, uint, bytes32, uint) {
//        if (betKey < emoDb.betsCount()) {
//            EmojillionaireDb.Bet bet = emoDb.bets[betKey];
//            return (bet.playerAddress, bet.guesses, bet.rolls, bet.guessesCost, bet.betFee, bet.jackpotKey,
//                    bet.queryId, bet.date);
//        }
//    }

    function getNumbersFromString(string s, string delimiter, uint16 howmany) internal returns(uint16[] numbers){
         strings.slice memory myresult = s.toSlice();
         strings.slice memory delim = delimiter.toSlice();
         numbers = new uint16[](howmany);
         for(uint8 i = 0; i < howmany; i++){
             numbers[i] = uint16(parseInt(myresult.split(delim).toString()));
         }
         return numbers;
    }

    function __callback(bytes32 queryId, string result) // 500k
    onlyActive {
        if (!privateNet && msg.sender != oraclize_cbAddress())
            throw;

        uint betKey;
        bool betKeyFound;
        (betKeyFound, betKey) = emoDb.getBetKeyByQueryId(queryId);

        if (!betKeyFound) {
            throw;
        }

        emoDb.addRolls(betKey, getNumbersFromString(result,"\n", uint16(emoDb.getBetGuessesCount(betKey))));
    }

    function withdraw()
    onlyActive {
        uint credit = emoDb.getPlayerCredit(msg.sender);
        if (credit > 0) {
            emoDb.changeCredit(msg.sender, 0, false);
            if (!msg.sender.send(credit)) throw;
        }
    }

    function developerWithdrawProfit()
        onlyDeveloper {
        if (!developer.send(this.balance - emoDb.currentJackpotAmount())) throw;
    }

    function() {
        throw;
    }
}