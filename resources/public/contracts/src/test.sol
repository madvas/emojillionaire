pragma solidity ^0.4.1;

contract Test {
    event onLogInt(uint num, string str);
    function Test() {}

    function blah() payable {
        onLogInt(1, "okay");
        throw;
    }
}