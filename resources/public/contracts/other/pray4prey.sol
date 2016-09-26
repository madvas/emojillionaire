contract mortal {
    address owner;

    function mortal() {
        owner = msg.sender;
    }

    function kill() internal{
        suicide(owner);
    }
}

contract Pray4Prey is mortal, usingOraclize {
	using strings for *;

    /**the balances in wei being held by each player */
    mapping(address => uint128) winBalances;
    /**list of all players*/
    address[] public players;
    /** the number of players (may be != players.length, since players can leave the game)*/
    uint16 public numPlayers;

    /** animals[0] -> list of the owners of the animals of type 0, animals[1] animals type 1 etc (using a mapping instead of a multidimensional array for lower gas consumptions) */
    mapping(uint8 => address[]) animals;
    /** the cost of each animal type */
    uint128[] public costs;
    /** the value of each animal type (cost - fee), so it's not necessary to compute it each time*/
    uint128[] public values;
    /** internal  array of the probability factors, so it's not necessary to compute it each time*/
    uint8[] probabilityFactors;

    /** the indices of the animals per type per player */
   // mapping(address => mapping(uint8 => uint16[])) animalIndices;
  // mapping(address => mapping(uint8 => uint16)) numAnimalsXPlayerXType;

    /** total number of animals in the game
    (!=sum of the lengths of the prey animals arrays, since those arrays contain holes) */
    uint16 public numAnimals;
    /** The maximum of animals allowed in the game */
    uint16 public maxAnimals;
    /** number of animals per player */
    mapping(address => uint8) numAnimalsXPlayer;
    /** number of animals per type */
    mapping(uint8 => uint16) numAnimalsXType;


    /** the fee to be paid each time an animal is bought in percent*/
    uint8 public fee;

    /** the query string getting the random numbers from oraclize**/
    string randomQuery;
    /** the timestamp of the next attack **/
    uint public nextAttackTimestamp;
    /** gas provided for oraclize callback (attack)**/
    uint32 public oraclizeGas;



    /** is fired when new animals are purchased (who bought how many animals of which type?) */
    event newPurchase(address player, uint8 animalType, uint8 amount);
    /** is fired when a player leaves the game */
    event newExit(address player, uint256 totalBalance);
    /** is fired when an attack occures*/
    event newAttack();


    /** expected parameters: the costs per animal type and the game fee in percent
    *   assumes that the cheapest animal is stored in [0]
    */
    function Pray4Prey(uint128[] animalCosts, uint8 gameFee) {
        costs = animalCosts;
        fee = gameFee;
        for(uint8 i = 0; i< costs.length; i++){
            values.push(costs[i]-costs[i]/100*fee);
            probabilityFactors.push(uint8(costs[costs.length-i-1]/costs[0]));
        }
        maxAnimals = 3000;
        randomQuery = "https://www.random.org/integers/?num=10&min=0&max=10000&col=1&base=10&format=plain&rnd=new";
        oraclizeGas=500000;
    }

     /** The fallback function runs whenever someone sends ether
        Depending of the value of the transaction the sender is either granted a prey or
        the transaction is discarded and no ether accepted
        In the first case fees have to be paid*/
     function (){
         for(uint8 i = 0; i < costs.length; i++)
            if(msg.value==costs[i])
                addAnimals(i);

        if(msg.value==1000000000000000)
            exit();
        else
            throw;

     }

     /** buy animals of a given type
     *  as many animals as possible are bought with msg.value, rest is added to the winBalance of the sender
     */
     function addAnimals(uint8 animalType){
        uint8 amount = uint8(msg.value/costs[animalType]);
        if(animalType >= costs.length || msg.value50 || numAnimals+amount>=maxAnimals) throw;
        //if type exists, enough ether was transferred, the player doesn't posess to many animals already (else exit is too costly) and there are less than 10000 animals in the game
        if(numAnimalsXPlayer[msg.sender]==0)//new player
            addPlayer();
        for(uint8 j = 0; j0){
                for(uint16 i = 0; i < numAnimalsXType[animalType]; i++){
                    if(animals[animalType][i] == playerAddress){
                       replaceAnimal(animalType,i, true);
                    }
                }
    	    }
        }
        numAnimals-=numAnimalsXPlayer[playerAddress];
        delete numAnimalsXPlayer[playerAddress];
    }


    /**
     * Replaces the animal at the given index with the last animal in the array
     * */
    function replaceAnimal(uint8 animalType, uint16 index, bool exit) internal{
        if(exit){//delete all animals at the end of the array that belong to the same player
            while(animals[animalType][numAnimalsXType[animalType]-1]==animals[animalType][index]){
                numAnimalsXType[animalType]--;
                delete animals[animalType][numAnimalsXType[animalType]];
                if(numAnimalsXType[animalType]==index)
                    return;
            }
        }
        numAnimalsXType[animalType]--;
		animals[animalType][index]=animals[animalType][numAnimalsXType[animalType]];
		delete animals[animalType][numAnimalsXType[animalType]];//actually there's no need for the delete, since the index will not be accessed since it's higher than numAnimalsXType[animalType]
    }



    /**
     * pays out the given player and removes his fishes.
     * amount = winbalance + sum(fishvalues)
     * returns true if payout was successful
     * */
    function payout(address playerAddress) internal returns(bool){
        return playerAddress.send(winBalances[playerAddress]);
    }


    /**
     * manually triggers the attack. cannot be called afterwards, except
     * by the owner if and only if the attack wasn't launched as supposed, signifying
     * an error ocurred during the last invocation of oraclize, or there wasn't enough ether to pay the gas
     * */
    function triggerAttackManually(uint32 inseconds){
        if(!(msg.sender==owner && nextAttackTimestamp < now+300)) throw;
        triggerAttack(inseconds);
    }

    /**
     * sends a query to oraclize in order to get random numbers in 'inseconds' seconds
     */
    function triggerAttack(uint32 inseconds) internal{
    	nextAttackTimestamp = now+inseconds;
    	oraclize_query(inseconds, "URL", randomQuery, oraclizeGas);
    }

    /**
     * The actual predator attack.
     * The predator kills up to 10 animals, but in case there are less than 100 animals in the game up to 10% get eaten.
     * Since there might be holes in the arrays holding the animals, it might be less.
     * */
    function __callback(bytes32 myid, string result) {
        if (msg.sender != oraclize_cbAddress()) throw; // just to be sure the calling address is the Oraclize authorized one

        uint16[] memory ranges = new uint16[](costs.length+1);
        ranges[0] = 0;
        for(uint8 animalType = 0; animalType < costs.length; animalType ++){
            ranges[animalType+1] = ranges[animalType]+uint16(probabilityFactors[animalType]*numAnimalsXType[animalType]);
        }
        uint128 pot;
        uint16 random;
        uint16 howmany = numAnimals<100?(numAnimals<10?1:numAnimals/10):10;//do not kill more than 10%, but at least one
        uint16[] memory randomNumbers = getNumbersFromString(result,"\n", howmany);
        for(uint8 i = 0; i < howmany; i++){
            random = mapToNewRange(randomNumbers[i], ranges[costs.length]);
            for(animalType = 0; animalType < costs.length; animalType ++)
                if (random < ranges[animalType+1]){
                    pot+= killAnimal(animalType, (random-ranges[animalType])/probabilityFactors[animalType]);
                    break;
                }
        }
        numAnimals-=howmany;
        newAttack();
        if(pot>uint128(oraclizeGas*tx.gasprice))
            distribute(uint128(pot-oraclizeGas*tx.gasprice));//distribute the pot minus the oraclize gas costs
        triggerAttack(timeTillNextAttack());
    }

    /**
     * the frequency of the shark attacks depends on the number of animals in the game.
     * many animals -> many shark attacks
     * at least one attack in 24 hours
     * */
    function timeTillNextAttack() constant internal returns(uint32){
        return (86400/(1+numAnimals/100));
    }


    /**
     * kills the animal of the given type at the given index.
     * */
    function killAnimal(uint8 animalType, uint16 index) internal returns(uint128){
        address preyOwner = animals[animalType][index];

        replaceAnimal(animalType,index,false);
        numAnimalsXPlayer[preyOwner]--;

        //numAnimalsXPlayerXType[preyOwner][animalType]--;
        //if the player still owns prey, the value of the animalType1 alone goes into the pot
        if(numAnimalsXPlayer[preyOwner]>0){
        	winBalances[preyOwner]-=values[animalType];
            return values[animalType];
        }
        //owner does not have anymore prey, his winBlanace goes into the pot
        else{
            uint128 bounty = winBalances[preyOwner];
            delete winBalances[preyOwner];
            deletePlayer(preyOwner);
            return bounty;
        }

    }


    /** distributes the given amount among the players depending on the number of fishes they possess*/
    function distribute(uint128 amount) internal{
        uint128 share = amount/numAnimals;
        for(uint16 i = 0; i < numPlayers; i++){
            winBalances[players[i]]+=share*numAnimalsXPlayer[players[i]];
        }
    }

    /**
     * allows the owner to collect the accumulated fees
     * sends the given amount to the owner's address if the amount does not exceed the
     * fees (cannot touch the players' balances) minus 100 finney (ensure that oraclize fees can be paid)
     * */
    function collectFees(uint128 amount){
        if(!(msg.sender==owner)) throw;
        uint collectedFees = getFees();
        if(amount + 100 finney < collectedFees){
            if(!owner.send(amount)) throw;
        }
    }

    /**
     * pays out the players and kills the game.
     * */
    function stop(){
        if(!(msg.sender==owner)) throw;
        for(uint16 i = 0; i< numPlayers; i++){
            payout(players[i]);
        }
        kill();
    }

    /**
     * adds a new animal type to the game
     * max. number of animal types: 100
     * the cost may not be lower than costs[0]
     * */
    function addAnimalType(uint128 cost){
        if(!(msg.sender==owner)||cost=100) throw;
        costs.push(cost);
        values.push(cost/100*fee);
        probabilityFactors.push(uint8(cost/costs[0]));
    }



   /****************** GETTERS *************************/


    function getWinBalancesOf(address playerAddress) constant returns(uint128){
        return winBalances[playerAddress];
    }

    function getAnimals(uint8 animalType) constant returns(address[]){
        return animals[animalType];
    }

    function getFees() constant returns(uint fees){
        uint reserved = 0;
        for(uint16 j = 0; j< numPlayers; j++)
            reserved+=winBalances[players[j]];
        return address(this).balance - reserved;
    }

    function getNumAnimalsXType(uint8 animalType) constant returns(uint16){
        return numAnimalsXType[animalType];
    }

    function getNumAnimalsXPlayer(address playerAddress) constant returns(uint16){
        return numAnimalsXPlayer[playerAddress];
    }

   /* function getNumAnimalsXPlayerXType(address playerAddress, uint8 animalType) constant returns(uint16){
        return numAnimalsXPlayerXType[playerAddress][animalType];
    }
    */
    /****************** SETTERS *************************/

    function setOraclizeGas(uint32 newGas){
        if(!(msg.sender==owner)) throw;
    	oraclizeGas = newGas;
    }

    function setMaxAnimals(uint16 number){
        if(!(msg.sender==owner)) throw;
    	maxAnimals = number;
    }

    /************* HELPERS ****************/

    /**
     * maps a given number to the new range (old range 10000)
     * */
    function mapToNewRange(uint number, uint range) constant internal returns (uint16 randomNumber) {
        return uint16(number*range / 10000);
    }

    /**
     * converts a string of numbers being separated by a given delimiter into an array of numbers (#howmany)
     */
     function getNumbersFromString(string s, string delimiter, uint16 howmany) constant internal returns(uint16[] numbers){
         strings.slice memory myresult = s.toSlice();
         strings.slice memory delim = delimiter.toSlice();
         numbers = new uint16[](howmany);
         for(uint8 i = 0; i < howmany; i++){
             numbers[i]= uint16(parseInt(myresult.split(delim).toString()));
         }
         return numbers;
     }

}

