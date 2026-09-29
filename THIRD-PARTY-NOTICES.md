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

CodeMirror and KaTeX are bundled into `editor.js` and the `katex` folder inside the
application's jar.

## MIT License (CodeMirror, Lezer and their libraries; KaTeX)

Copyright (C) 2018-2025 by Marijn Haverbeke <marijn@haverbeke.berlin> and others
(CodeMirror, Lezer, style-mod, w3c-keyname, crelt, find-cluster-break; the JSON grammar
also by Arun Srinivasan)

Copyright (c) 2013-2020 Khan Academy and other contributors (KaTeX)

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
