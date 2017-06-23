pragma solidity ^0.4.11;

contract DistrictVoting {
    event onVote(address indexed voter, uint16 indexed candidate);

    function vote(uint16 candidate) {
        onVote(msg.sender, candidate);
    }
}