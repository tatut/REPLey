import {EditorView, keymap, highlightSpecialChars, drawSelection, highlightActiveLine, dropCursor,
        rectangularSelection, crosshairCursor,
        lineNumbers, highlightActiveLineGutter} from "@codemirror/view"
import {EditorState} from "@codemirror/state" // Extension
import {defaultHighlightStyle, syntaxHighlighting, indentOnInput, bracketMatching,
        foldGutter, foldKeymap} from "@codemirror/language"
import {defaultKeymap, history, historyKeymap} from "@codemirror/commands"
//import {searchKeymap, highlightSelectionMatches} from "@codemirror/search"
import {autocompletion, completionKeymap, closeBrackets, closeBracketsKeymap} from "@codemirror/autocomplete"
//import {lintKeymap} from "@codemirror/lint"

function evalKeymap(evalfn) {
    return [{
        key: "Cmd-Enter",
        run(v) { evalfn(v.state.doc.toString()); return true }
    }];
}

export function initREPL(evalfn) {
    let repl = new EditorView({
        doc: "",
        extensions: [
            lineNumbers(),
            highlightActiveLineGutter(),
            highlightSpecialChars(),
            history(),
            foldGutter(),
            drawSelection(),
            dropCursor(),
            EditorState.allowMultipleSelections.of(true),
            indentOnInput(),
            syntaxHighlighting(defaultHighlightStyle, {fallback: true}),
            bracketMatching(),
            closeBrackets(),
            autocompletion(),
            rectangularSelection(),
            crosshairCursor(),
            highlightActiveLine(),
            //        highlightSelectionMatches(),
            keymap.of([
                ...(evalKeymap(evalfn)),
                ...closeBracketsKeymap,
                ...defaultKeymap,
                //          ...searchKeymap,
                ...historyKeymap,
                ...foldKeymap,
                ...completionKeymap //,
                //            ...lintKeymap
            ])
        ],
        parent: document.querySelector('#repl-container')
    });
    return repl;
}
