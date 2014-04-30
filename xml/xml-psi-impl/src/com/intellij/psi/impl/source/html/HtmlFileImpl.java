/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.html;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceResolver;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author maxim
 */
public class HtmlFileImpl extends XmlFileImpl implements FileReferenceResolver {
  public HtmlFileImpl(FileViewProvider provider) {
    super(provider, XmlElementType.HTML_FILE);
  }

  public String toString() {

    return "HtmlFile:" + getName();
  }

  @Override
  public XmlDocument getDocument() {
    CompositeElement treeElement = calcTreeElement();

    ASTNode node = treeElement.findChildByType(XmlElementType.HTML_DOCUMENT);
    return node != null ? (XmlDocument)node.getPsi() : null;
  }

  @Nullable
  @Override
  public PsiFileSystemItem resolveFileReference(@NotNull FileReference reference, @NotNull String name) {
    VirtualFile file = getVirtualFile();
    if (!(file instanceof HttpVirtualFile)) {
      return null;
    }

    VirtualFile childFile = file.findChild(name);
    HttpFileSystem fileSystem = (HttpFileSystem)getVirtualFile().getFileSystem();
    if (childFile == null) {
      childFile = fileSystem.createChild(getVirtualFile(), name, !reference.isLast());
    }
    if (childFile.isDirectory()) {
      // pre create children
      VirtualFile childParent = childFile;
      FileReference[] references = reference.getFileReferenceSet().getAllReferences();
      for (int i = reference.getIndex() + 1, n = references.length; i < n; i++) {
        FileReference childReference = references[i];
        childParent = fileSystem.createChild(childParent, childReference.decode(childReference.getText()), i != (n - 1));
      }

      return getManager().findDirectory(childFile);
    }
    else {
      return getManager().findFile(childFile);
    }
  }
}
