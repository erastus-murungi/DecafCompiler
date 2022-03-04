package edu.mit.compilers.symbolTable;

import java.util.HashMap;
import java.util.Optional;

import edu.mit.compilers.descriptors.Descriptor;


public class SymbolTable {
    private final edu.mit.compilers.symbolTable.SymbolTable parent;
    private final SymbolTableType symbolTableType;
    private final HashMap<String, Descriptor> entries = new HashMap<>();

    public SymbolTable(SymbolTable parent, SymbolTableType symbolTableType) {
        super();
        this.parent = parent;
        this.symbolTableType = symbolTableType;
    }

    /**
     * @param key Look up a variable only within the current scope
     * @return true if the descriptor for key is found else false
     */

    public boolean containsEntry(String key) {
        return entries.containsKey(key);
    }

    /**
     * Look up a variable recursively up the scope hierarchy
     *
     * @param stringId the id to lookup in the symbol table hierarchy
     * @return Optional.empty if the descriptor is not found else Optional[Descriptor]
     */
    public Optional<Descriptor> getDescriptorFromValidScopes(String stringId) {
        Descriptor currentDescriptor = entries.get(stringId);
        if (currentDescriptor == null) {
            if (parent != null)
                return parent.getDescriptorFromValidScopes(stringId);
        }
        return Optional.ofNullable(currentDescriptor);
    }
}
