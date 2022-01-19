class Uuid {
    private static lut = [];
    static initialize = () => {
        for (var i=0; i<256; i++) {
            Uuid.lut[i] = (i < 16 ? '0' : '') + (i).toString(16);
        }
        delete Uuid.initialize;
    };

    static v4(): string {
        var d0 = Math.random()*0xffffffff|0;
        var d1 = Math.random()*0xffffffff|0;
        var d2 = Math.random()*0xffffffff|0;
        var d3 = Math.random()*0xffffffff|0;
        return Uuid.lut[d0&0xff]+Uuid.lut[d0>>8&0xff]+Uuid.lut[d0>>16&0xff]+Uuid.lut[d0>>24&0xff]+'-'+
            Uuid.lut[d1&0xff]+Uuid.lut[d1>>8&0xff]+'-'+Uuid.lut[d1>>16&0x0f|0x40]+Uuid.lut[d1>>24&0xff]+'-'+
            Uuid.lut[d2&0x3f|0x80]+Uuid.lut[d2>>8&0xff]+'-'+Uuid.lut[d2>>16&0xff]+Uuid.lut[d2>>24&0xff]+
            Uuid.lut[d3&0xff]+Uuid.lut[d3>>8&0xff]+Uuid.lut[d3>>16&0xff]+Uuid.lut[d3>>24&0xff];
    }
}
module Uuid {
    Uuid.initialize();
}

export default Uuid;
