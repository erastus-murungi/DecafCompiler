package edu.mit.compilers.parse;

import edu.mit.compilers.utils.Pair;

import java.util.ArrayList;
import java.util.List;

public class FieldDeclaration extends Node {
  final BuiltinType typeNode;
  final List<Name> names;
  final List<Array> arrays;

  public FieldDeclaration(
      BuiltinType typeNode,
      List<Name> names,
      List<Array> arrays) {
    this.typeNode = typeNode;
    this.names = names;
    this.arrays = arrays;
  }

  @Override
  public List<Pair<String, Node>> getChildren() {
    ArrayList<Pair<String, Node>> pairArrayList = new ArrayList<>();
    pairArrayList.add(new Pair<>("type", typeNode));
    for (Name name: names)
        pairArrayList.add(new Pair<>("var", name));
    for (Array array: arrays)
        pairArrayList.add(new Pair<>("array", array));
    return pairArrayList;
  }

    @Override
    public boolean isTerminal() {
        return false;
    }

    @Override
    public String toString() {
        return "FieldDeclaration{" + "type=" + typeNode + ", names=" + names + ", arrays=" + arrays + '}';
    }
}
