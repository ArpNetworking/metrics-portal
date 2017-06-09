/*
 * Copyright 2014 Groupon.com
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

class Color {
    r: number;
    g: number;
    b: number;
    a: number = 1;

    constructor(r: number, g: number, b: number, a: number = 1) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    rgb(): number[] {
        return [this.r, this.g, this.b];
    }

    static of(colorString: string): Color {
        if (colorString.charAt(0) == "#" && (colorString.length == 7 || colorString.length == 9)) {
            let parse = colorString.slice(1);
            let red = parseInt(parse.slice(0, 2), 16);
            let green = parseInt(parse.slice(2, 4), 16);
            let blue = parseInt(parse.slice(4, 6), 16);
            let alpha = 1;
            if (parse.length == 8) {
                alpha = parseInt(parse.slice(6, 8), 16);
            }
            return new Color(red, green, blue, alpha);
        } else {
            throw new Error("Invalid color string " + colorString);
        }


    }
}

export = Color;
