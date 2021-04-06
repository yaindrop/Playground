const factorialArr = [
    1, 2, 6, 24, 
    120, 720, 5040, 40320, 
    362880, 3628800, 39916800, 479001600, 
    6227020800, 87178291200, 1307674368000, 20922789888000, 
    355687428096000, 6402373705728000, 121645100408832000, 2432902008176640000
]
const factorial: (n: number) => number = n => factorialArr[n - 1]
const permuNum = factorial

const advance: (p: number[], index: number, byAmount: number) => void = (p, i, a) => {
    const t = p[i]
    for (var j = i - 1; j >= i - a; j --) 
        p[j + 1] = p[j]
    p[i - a] = t
}

const idToPermu: (id: number, plen?: number, p?: number[]) => number[] = (id, plen, p) => {
    var i = 1
    for (; permuNum(i) <= id && i <= factorialArr.length; i ++) // possible optmization w/ binary search
        continue
    if (!plen) plen = i
    if (!p) p = new Array(plen).fill(0).map((_, i) => i)  // [0 ... i - 1]
    if (i == 1) return p
    const selectedIndex = plen - i
    const subPermuNum = permuNum(i - 1)
    const subPermuIndex = Math.floor(id / subPermuNum)
    const subPermuId = id % subPermuNum
    advance(p, selectedIndex + subPermuIndex, subPermuIndex) // possible optmization w/ balanced bst
    idToPermu(subPermuId, plen, p)
    return p
}

const permuToId: (p: number[]) => number = p => {
    var r = 0
    for (var i = 0; i < p.length - 1; i ++) { // possible optmization w/ sort counting
        var numMisPlace = 0
        for (var j = i + 1; j < p.length; j ++) 
            if (p[j] < p[i]) numMisPlace ++
        r += numMisPlace * permuNum(p.length - i - 1)
    }
    return r
}

new Array(permuNum(5)).fill(0).map((_, i) => idToPermu(i)).forEach((p, i) => 
    console.log(i + ' -> ' + p.join('') + ' -> ' + permuToId(p))
)
