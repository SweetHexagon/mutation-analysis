package com.example.pojo;

import com.example.parser.LanguageParser;
import lombok.AllArgsConstructor;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

@AllArgsConstructor
public class ParsedFile {
    public final ParseTree tree;
    public final CommonTokenStream tokens;
    public final LanguageParser parser;
}

