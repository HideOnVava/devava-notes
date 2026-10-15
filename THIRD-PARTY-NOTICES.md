# Third-party notices

Devava Notes is released under the MIT license (see [`LICENSE`](LICENSE)). The application
includes the following third-party software, each under its own license.

| Component | Version | License | Copyright |
| --- | --- | --- | --- |
| [OpenJDK](https://openjdk.org) runtime, trimmed by jlink (its notices are in `runtime\legal`) | 21 | GPL-2.0 with the Classpath Exception | Oracle and/or its affiliates |
| [OpenJFX](https://openjfx.io) (JavaFX) | 21.0.6 | GPL-2.0 with the Classpath Exception | Oracle and/or its affiliates |
| [Gson](https://github.com/google/gson) | 2.10.1 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | Google Inc. |
| [commonmark-java](https://github.com/commonmark/commonmark-java), with its GFM tables, strikethrough and task list extensions | 0.30.0 | BSD-2-Clause (below) | Atlassian Pty Ltd |
| [CodeMirror 6](https://codemirror.net) (`@codemirror/*`) and its libraries `@lezer/*`, `style-mod`, `w3c-keyname`, `crelt`, `@marijn/find-cluster-break` | 6 | MIT (below) | Marijn Haverbeke and others |
| [KaTeX](https://katex.org), with its fonts | 0.18.9 | MIT (below) | Khan Academy and other contributors |
| [Excalidraw](https://github.com/excalidraw/excalidraw) (`@excalidraw/excalidraw`), with `@excalidraw/laser-pointer`, `@excalidraw/mermaid-to-excalidraw` and `@excalidraw/markdown-to-text` | 0.18.1 | MIT (below) | Excalidraw; Daniel Esteves |
| [React](https://react.dev) and React DOM, with `scheduler` and `use-sync-external-store` | 18.3.1 | MIT (below) | Meta Platforms, Inc. and affiliates |
| [Mermaid](https://mermaid.js.org), with Cytoscape.js and its layouts, dagre-d3-es, marked, khroma, stylis, Day.js and the other libraries listed with the MIT License below | 11.17.2 | MIT (below); marked also under the Markdown license (below) | Knut Sveidqvist, and the authors named below |
| [D3](https://d3js.org) (`d3`, its `d3-*` modules and `internmap`) | 7.9.0 | ISC (below); `d3-ease` and `d3-sankey` BSD-3-Clause (below); the ColorBrewer schemes of `d3-scale-chromatic` [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | Mike Bostock and others (below) |
| [DOMPurify](https://github.com/cure53/DOMPurify) | 3.4.17 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) (it is also offered under MPL-2.0) | Dr.-Ing. Mario Heiderich, Cure53 |
| [browser-fs-access](https://github.com/GoogleChromeLabs/browser-fs-access) | 0.29.1 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | Google LLC |
| [crc-32](https://github.com/SheetJS/js-crc32) | 0.3.0 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | SheetJS |
| [pako](https://github.com/nodeca/pako) | 2.0.3 | MIT and Zlib (below) | Vitaly Puzrin and Andrei Tuputcyn; Jean-loup Gailly and Mark Adler |
| [fractional-indexing](https://github.com/rocicorp/fractional-indexing) | 3.2.0 | CC0-1.0 (no conditions) | Rocicorp |
| [tslib](https://github.com/microsoft/tslib) | 2.8.1 | 0BSD (below) | Microsoft Corporation |
| Excalidraw's fonts Excalifont, Virgil, Nunito, Lilita One, Assistant and Cascadia Code | | SIL Open Font License 1.1 (below) | named below |
| Comic Shanns, one of Excalidraw's fonts | 1.3.0 | MIT (below) | Shannon Miwa and others (below) |

CodeMirror and KaTeX are bundled into `editor.js` and the `katex` folder inside the
application's jar; Excalidraw, React, Mermaid and the other libraries into `draw.js`, and
Excalidraw's fonts are in its `fonts` folder (converted from WOFF2 to WOFF, with no other
change). Excalidraw's Xiaolai and Liberation Sans fonts are not included.

## MIT License (CodeMirror, Lezer and their libraries; KaTeX; Excalidraw, React, Mermaid and their libraries; Comic Shanns)

Copyright (C) 2018-2025 by Marijn Haverbeke <marijn@haverbeke.berlin> and others
(CodeMirror, Lezer, style-mod, w3c-keyname, crelt, find-cluster-break; the JSON grammar
also by Arun Srinivasan)

Copyright (c) 2013-2020 Khan Academy and other contributors (KaTeX)

Copyright (c) 2020 Excalidraw (Excalidraw); Copyright (c) 2023 Excalidraw (laser-pointer,
mermaid-to-excalidraw); Copyright (c) 2020 Daniel Esteves (markdown-to-text)

Copyright (c) Facebook, Inc. and its affiliates; Copyright (c) Meta Platforms, Inc. and
affiliates (React, React DOM, scheduler, use-sync-external-store)

Copyright (c) 2014 - 2022 Knut Sveidqvist (Mermaid)

Copyright (c) 2016-2026, The Cytoscape Consortium (Cytoscape.js, cytoscape-cose-bilkent);
Copyright (c) 2018 - present, iVis-at-Bilkent (cytoscape-fcose, cose-base, layout-base)

Copyright (c) 2012-2014 Chris Pettitt; Copyright (c) 2022-2024 Thibaut Lassalle, David
Newell, Alois Klink, Sidharth Vinod and dagre-es contributors (dagre-d3-es)

Copyright (c) 2018+, MarkedJS; Copyright (c) 2011-2018, Christopher Jeffrey (marked)

Copyright (c) 2019-present Fabio Spampinato, Andrew Maney (khroma); Copyright (c)
2016-present Sultan Tarimo (stylis); Copyright (c) 2018-present, iamkun (Day.js);
Copyright (c) 2018 Tamino Martinius (ts-dedent); Copyright (c) 2021-present Vjacheslav
Trushkin (@iconify/utils); Copyright (c) 2013 Ben Frederickson, Copyright (c) 2021 Samuel
Gratzl (@upsetjs/venn.js)

Copyright OpenJS Foundation and other contributors (lodash-es, lodash.debounce,
lodash.throttle)

Copyright (c) 2019-2023 Preet Shihn (Rough.js, points-on-curve, points-on-path,
path-data-parser, hachure-fill); Copyright (c) 2021 Stephen Ruiz Ltd (perfect-freehand)

Copyright (C) 2014-2020 Vitaly Puzrin and other authors (pica, image-blob-reduce);
Copyright (C) 2014-2017 by Vitaly Puzrin and Andrei Tuputcyn (pako)

Copyright (c) 2015 Hugh Kennedy (png-chunk-text, png-chunks-encode, png-chunks-extract)

Copyright (c) 2022 WorkOS (Radix UI, `@radix-ui/*`); Copyright (c) 2021-present Floating
UI contributors (`@floating-ui/*`)

Copyright (c) 2020-2023 Poimandres (Jotai, tunnel-rat); Copyright (c) 2023 Daishi Kato
(jotai-scope); Copyright (c) 2019 Paul Henschel (Zustand)

Copyright (c) 2017-2020 Anton Korzunov (react-remove-scroll, react-remove-scroll-bar,
react-style-singleton, use-callback-ref, use-sidecar, aria-hidden, get-nonce)

Copyright 2017 Andrey Sitnik (nanoid); Copyright (c) 2010-2020 Robert Kieffer and other
contributors (uuid); Copyright (c) 2017 Braintree (@braintree/sanitize-url); Copyright (c)
Luke Edwards (clsx); Copyright (c) 2016 heeyeun (Open Color); Copyright (c) 2012 Matt York
(fuzzy); Copyright (c) 2017 Tim De Pauw (es6-promise-pool); Copyright (c) Wilson Page
(FastDom); Copyright (c) 2021 Tristan Fraipont (canvas-roundrect-polyfill); Copyright (c)
2024 Viva Republica, Inc. (es-toolkit); Copyright (c) 2012 Aaron Heckmann (sliced);
Copyright (c) 2014-present Sebastian McKenzie and other contributors (@babel/runtime)

Copyright (c) 2018 Shannon Miwa; Copyright (c) 2023 Jesus Gonzalez; Copyright (c) 2023
Rodrigo Batista de Moraes; Copyright (c) 2024 Fini Jastrow; Copyright (c) 2024 Kyle
Beechly (Comic Shanns)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

## BSD 2-Clause License (commonmark-java)

Copyright (c) 2015, Atlassian Pty Ltd
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## ISC License (D3)

Copyright 2010-2026 Mike Bostock (D3 and its modules, internmap); Copyright 2018-2021
Observable, Inc. and Copyright 2021 Mapbox (d3-delaunay); Copyright 2008-2012 Charles
Karney (d3-geo)

Permission to use, copy, modify, and/or distribute this software for any purpose
with or without fee is hereby granted, provided that the above copyright notice
and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS
OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.

The ColorBrewer color schemes in d3-scale-chromatic are Copyright 2002 Cynthia Brewer,
Mark Harrower, and The Pennsylvania State University, licensed under the Apache License,
Version 2.0.

## BSD 3-Clause License (d3-ease, d3-sankey)

Copyright 2010-2021 Mike Bostock; Copyright 2001 Robert Penner (d3-ease)

Copyright 2015, Mike Bostock (d3-sankey)

All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the author nor the names of contributors may be used to
  endorse or promote products derived from this software without specific prior
  written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Markdown License (marked)

Copyright © 2004, John Gruber
http://daringfireball.net/
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this list of
  conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice, this list of
  conditions and the following disclaimer in the documentation and/or other materials
  provided with the distribution.
* Neither the name “Markdown” nor the names of its contributors may be used to endorse or
  promote products derived from this software without specific prior written permission.

This software is provided by the copyright holders and contributors “as is” and any
express or implied warranties, including, but not limited to, the implied warranties of
merchantability and fitness for a particular purpose are disclaimed. In no event shall the
copyright owner or contributors be liable for any direct, indirect, incidental, special,
exemplary, or consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or business interruption)
however caused and on any theory of liability, whether in contract, strict liability, or
tort (including negligence or otherwise) arising in any way out of the use of this
software, even if advised of the possibility of such damage.

## Zlib License (pako, its port of zlib)

Copyright (C) 1995-2013 Jean-loup Gailly and Mark Adler
Copyright (C) 2014-2017 Vitaly Puzrin and Andrey Tupitsin

This software is provided 'as-is', without any express or implied
warranty. In no event will the authors be held liable for any damages
arising from the use of this software.

Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter it and redistribute it
freely, subject to the following restrictions:

1. The origin of this software must not be misrepresented; you must not
   claim that you wrote the original software. If you use this software
   in a product, an acknowledgment in the product documentation would be
   appreciated but is not required.
2. Altered source versions must be plainly marked as such, and must not be
   misrepresented as being the original software.
3. This notice may not be removed or altered from any source distribution.

## Zero-Clause BSD License (tslib)

Copyright (c) Microsoft Corporation.

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.

## SIL Open Font License 1.1 (Excalidraw's fonts)

Excalifont: Copyright (c) 2024 by Excalidraw.
Virgil: Copyright (c) 2011 by Your Own Font Foundry.
Nunito: Copyright 2014 The Nunito Project Authors (https://github.com/googlefonts/nunito).
Lilita One: Copyright (c) 2011 Juan Montoreano (juan@remolacha.biz), with Reserved Font
Names "Lilita One".
Assistant: Copyright 2020 The Assistant Project Authors (https://github.com/hafontia/Assistant).
Copyright 2010 The Source Sans Pro Authors (https://github.com/adobe-fonts/source-sans-pro),
with Reserved Font Name 'Source'. Source is a trademark of Adobe Systems Incorporated in the
United States and/or other countries.
Cascadia Code: Copyright (c) 2019 - Present, Microsoft Corporation, with Reserved Font Name
Cascadia Code.

This Font Software is licensed under the SIL Open Font License, Version 1.1.

```
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```
