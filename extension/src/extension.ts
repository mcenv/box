import { ExtensionContext, RelativePattern, workspace } from "vscode";
import { LanguageClient, LanguageClientOptions, ServerOptions } from "vscode-languageclient/node";

let client: LanguageClient | undefined;

export function activate(context: ExtensionContext) {
  const folder = workspace.workspaceFolders!![0];
  const serverOptions: ServerOptions = {
    command: `mcx${process.platform === "win32" ? ".bat" : ""}`,
    args: ["lsp"],
    options: {
      cwd: folder.uri.fsPath,
    },
  };
  const clientOptions: LanguageClientOptions = {
    documentSelector: [{
      scheme: "file",
      language: "mcx",
      pattern: `${folder.uri.fsPath}/src/**/*.mcx`,
    }],
    synchronize: {
      fileEvents: workspace.createFileSystemWatcher(new RelativePattern(folder, "pack.json")),
    },
  };
  client = new LanguageClient(
    "mcx",
    serverOptions,
    clientOptions,
  );
  client.start();
}

export function deactivate() {
  return client?.stop();
}
