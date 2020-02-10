package com.ringdroid.data.model.soundfile

class Atom {
    // get the size of the this atom.
    // note: latest versions of spec simply call it 'box' instead of 'atom'.
    var size // includes atom header (8 bytes)
            : Int
        private set
    var typeInt: Int
        private set
    var data // an atom can either contain data or children, but not both.
            : ByteArray?
        private set
    private var mChildren: Array<Atom?>?
    private var mVersion // if negative, then the atom does not contain version and flags data.
            : Byte
    private var mFlags: Int

    // create an empty atom of the given type.
    constructor(type: String) {
        size = 8
        typeInt = getTypeInt(type)
        data = null
        mChildren = null
        mVersion = -1
        mFlags = 0
    }

    // create an empty atom of type type, with a given version and flags.
    constructor(type: String, version: Byte, flags: Int) {
        size = 12
        typeInt = getTypeInt(type)
        data = null
        mChildren = null
        mVersion = version
        mFlags = flags
    }

    // set the size field of the atom based on its content.
    private fun setSize() {
        var size = 8 // type + size
        if (mVersion >= 0) {
            size += 4 // version + flags
        }
        if (data != null) {
            size += data!!.size
        } else if (mChildren != null) {
            for (child in mChildren!!) {
                size += child!!.size
            }
        }
        this.size = size
    }

    private fun getTypeInt(type_str: String): Int {
        var type = 0
        type = type or type_str[0].toInt() shl 24
        type = type or type_str[1].toInt() shl 16
        type = type or type_str[2].toInt() shl 8
        type = type or type_str[3].toInt()
        return type
    }

    val typeStr: String
        get() {
            var type = ""
            type += (typeInt shr 24 and 0xFF).toByte().toChar()
            type += (typeInt shr 16 and 0xFF).toByte().toChar()
            type += (typeInt shr 8 and 0xFF).toByte().toChar()
            type += (typeInt and 0xFF).toByte().toChar()
            return type
        }

    fun setData(data: ByteArray?): Boolean {
        if (mChildren != null || data == null) { // TODO(nfaralli): log something here
            return false
        }
        this.data = data
        setSize()
        return true
    }

    fun addChild(child: Atom?): Boolean {
        if (data != null || child == null) { // TODO(nfaralli): log something here
            return false
        }
        var numChildren = 1
        if (mChildren != null) {
            numChildren += mChildren!!.size
        }
        val children = arrayOfNulls<Atom>(numChildren)
        if (mChildren != null) {
            System.arraycopy(mChildren, 0, children, 0, mChildren!!.size)
        }
        children[numChildren - 1] = child
        mChildren = children
        setSize()
        return true
    }

    // return the child atom of the corresponding type.
// type can contain grand children: e.g. type = "trak.mdia.minf"
// return null if the atom does not contain such a child.
    fun getChild(type: String): Atom? {
        return when (mChildren) {
            null -> {
                null
            }
            else -> {
                val types = type.split("\\.", 2.toBoolean()).toTypedArray()
                for (child in mChildren!!) {
                    if (child!!.typeStr == types[0]) {
                        if (types.size == 1) {
                            return child
                        } else {
                            return child.getChild(types[1])
                        }
                    }
                }
                null
            }
        }
    }


    // return a byte array containing the full content of the atom (including header)
    val bytes: ByteArray
        get() {
            val atom_bytes = ByteArray(size)
            var offset = 0
            atom_bytes[offset++] = (size shr 24 and 0xFF).toByte()
            atom_bytes[offset++] = (size shr 16 and 0xFF).toByte()
            atom_bytes[offset++] = (size shr 8 and 0xFF).toByte()
            atom_bytes[offset++] = (size and 0xFF).toByte()
            atom_bytes[offset++] = (typeInt shr 24 and 0xFF).toByte()
            atom_bytes[offset++] = (typeInt shr 16 and 0xFF).toByte()
            atom_bytes[offset++] = (typeInt shr 8 and 0xFF).toByte()
            atom_bytes[offset++] = (typeInt and 0xFF).toByte()
            if (mVersion >= 0) {
                atom_bytes[offset++] = mVersion
                atom_bytes[offset++] = (mFlags shr 16 and 0xFF).toByte()
                atom_bytes[offset++] = (mFlags shr 8 and 0xFF).toByte()
                atom_bytes[offset++] = (mFlags and 0xFF).toByte()
            }
            if (data != null) {
                System.arraycopy(data, 0, atom_bytes, offset, data!!.size)
            } else if (mChildren != null) {
                var child_bytes: ByteArray
                for (child in mChildren!!) {
                    child_bytes = child!!.bytes
                    System.arraycopy(child_bytes, 0, atom_bytes, offset, child_bytes.size)
                    offset += child_bytes.size
                }
            }
            return atom_bytes
        }

    // Used for debugging purpose only.
    override fun toString(): String {
        var str = ""
        val atom_bytes = bytes
        for (i in atom_bytes.indices) {
            if (i % 8 == 0 && i > 0) {
                str += '\n'
            }
            str += String.format("0x%02X", atom_bytes[i])
            if (i < atom_bytes.size - 1) {
                str += ','
                if (i % 8 < 7) {
                    str += ' '
                }
            }
        }
        str += '\n'
        return str
    }
}