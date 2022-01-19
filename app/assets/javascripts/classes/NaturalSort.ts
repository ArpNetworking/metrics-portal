/*
 * Copyright 2016 Groupon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

module NaturalSort {
    export var naturalSort = (a: string, b: string): number => {
        var aIndex: number = 0;
        var bIndex: number = 0;
        while (aIndex < a.length && bIndex < b.length) {
            var aChar = a.charCodeAt(aIndex);
            var bChar = b.charCodeAt(bIndex);
            if (aChar >= 48 && aChar <= 57) {
                if (bChar >= 48 && bChar <= 57) {
                    // They are both numbers
                    // consume the numbers completely, parse, then compare
                    var aNumber = "";
                    var bNumber = "";
                    while (aIndex < a.length && a.charCodeAt(aIndex) >= 48 && a.charCodeAt(aIndex) <= 57) {
                        aNumber += a.charAt(aIndex);
                        aIndex++;
                    }

                    while (bIndex < b.length && b.charCodeAt(bIndex) >= 48 && b.charCodeAt(bIndex) <= 57) {
                        bNumber += b.charAt(bIndex);
                        bIndex++;
                    }

                    var aInt = parseInt(aNumber);
                    var bInt = parseInt(bNumber);
                    if (aInt < bInt) {
                        return -1;
                    } else if (aInt > bInt) {
                        return 1;
                    }
                } else {
                    // aChar is numeric, bChar is non-numeric
                    return -1;
                }
            } else if (bChar >= 48 && aChar <= 57) {
                // bChar is numeric, but aChar is not
                return 1;
            } else {
                if (aChar < bChar) {
                    return -1;
                } else if (aChar > bChar) {
                    return 1;
                } else {
                    aIndex++;
                    bIndex++;
                }
            }
        }
        if (a.length < b.length) {
            return -1;
        } else if (a.length > b.length) {
            return 1;
        } else {
            return 0;
        }
    }
}
export default NaturalSort;
