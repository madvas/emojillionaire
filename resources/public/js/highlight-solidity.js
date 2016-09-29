var hljsDefineSolidity = function(hljs) {
    var SOL_KEYWORDS = {
        keyword:
            'var bool string ' +
            'int uint int8 uint8 int16 uint16 int24 uint24 int32 uint32 ' +
            'int40 uint40 int48 uint48 int56 uint56 int64 uint64 ' +
            'int72 uint72 int80 uint80 int88 uint88 int96 uint96 ' +
            'int104 uint104 int112 uint112 int120 uint120 int128 uint128 ' +
            'int136 uint136 int144 uint144 int152 uint152 int160 uint160 ' +
            'int168 uint168 int176 uint176 int184 uint184 int192 uint192 ' +
            'int200 uint200 int208 uint208 int216 uint216 int224 uint224 ' +
            'int232 uint232 int240 uint240 int248 uint248 int256 uint256 ' +
            'byte bytes bytes1 bytes2 bytes3 bytes4 bytes5 bytes6 bytes7 bytes8 ' +
            'bytes9 bytes10 bytes11 bytes12 bytes13 bytes14 bytes15 bytes16 ' +
            'bytes17 bytes18 bytes19 bytes20 bytes21 bytes22 bytes23 bytes24 ' +
            'bytes25 bytes26 bytes27 bytes28 bytes29 bytes30 bytes31 bytes32 ' +
            'enum struct mapping address ' +

            'new delete ' +
            'if else for while continue break return throw ' +

            'function modifier event ' +
            'constant anonymous indexed ' +
            'storage memory ' +
            'external public internal private returns ' +

            'import using ' +
            'contract library ' +
            'assembly',
        literal:
            'true false ' +
            'wei szabo finney ether ' +
            'second seconds minute minutes hour hours day days week weeks year years',
        built_in:
            'self ' +   // :NOTE: not a real keyword, but a convention used in storage manipulation libraries
            'this super selfdestruct ' +
            'now ' +
            'msg ' +
            'block ' +
            'tx ' +
            'sha3 sha256 ripemd160 erecover addmod mulmod ' +
            // :NOTE: not really toplevel, but advantageous to have highlighted as if reserved to
            //        avoid newcomers making mistakes due to accidental name collisions.
            'send call callcode delegatecall',
    };

    var SOL_NUMBER = {
        cN: 'number',
        v: [
            { b: '\\b(0[bB][01]+)' },
            { b: '\\b(0[oO][0-7]+)' },
            { b: hljs.CNR },
        ],
        r: 0,
    };

    var SOL_FUNC_PARAMS = {
        cN: 'params',
        b: /\(/, end: /\)/,
        eB: true,
        eE: true,
        k: SOL_KEYWORDS,
        c: [
            hljs.CLCM,
            hljs.CBCM,
            hljs.ASM,
            hljs.QSM,
            SOL_NUMBER,
        ],
    };

    var SOL_RESERVED_MEMBERS = {
        b: /\.\s*/,  // match any property access up to start of prop
        e: /[^A-Za-z0-9$_\.]/,
        eB: true,
        eE: true,
        k: {
            built_in: 'gas value send call callcode delegatecall balance length push',
        },
        r: 2,
    };

    function makeBuiltinProps(obj, props) {
        return {
            b: obj + '\\.\\s*',
            e: /[^A-Za-z0-9$_\.]/,
            eB: false,
            eE: true,
            k: {
                built_in: obj + ' ' + props,
            },
            c: [
                SOL_RESERVED_MEMBERS,
            ],
            r: 10,
        };
    }

    return {
        aliases: ['sol'],
        k: SOL_KEYWORDS,
        c: [
            // basic literal definitions
            hljs.ASM,
            hljs.QSM,
            hljs.CLCM,
            hljs.CBCM,
            SOL_NUMBER,
            { // functions
                cN: 'function',
                bK: 'function modifier event', e: /[{;]/, eE: true,
                c: [
                    hljs.inherit(hljs.TM, {
                        b: /[A-Za-z$_][0-9A-Za-z$_]*/,
                        k: SOL_KEYWORDS,
                    }),
                    SOL_FUNC_PARAMS,
                ],
                i: /\[|%/,
            },
            // built-in members
            makeBuiltinProps('msg', 'data sender sig'),
            makeBuiltinProps('block', 'blockhash coinbase difficulty gaslimit number timestamp '),
            makeBuiltinProps('tx', 'gasprice origin'),
            SOL_RESERVED_MEMBERS,
            { // contracts & libraries
                cN: 'class',
                bK: 'contract library', end: /[{]/, excludeEnd: true,
                i: /[:"\[\]]/,
                c: [
                    { bK: 'is' },
                    hljs.UTM,
                    SOL_FUNC_PARAMS,
                ],
            },
            { // imports
                bK: 'import', end: '[;$]',
                k: 'import * from as',
                c: [
                    hljs.ASM,
                    hljs.QSM,
                ],
            },
        ],
        i: /#/,
    };
}

hljs.registerLanguage('solidity', hljsDefineSolidity);