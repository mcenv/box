import { ExtensionContext } from "vscode";
import { LanguageClient, LanguageClientOptions, ServerOptions } from "vscode-languageclient/node";

let client: LanguageClient | undefined;

export function activate(context: ExtensionContext) {
  const serverOptions: ServerOptions = {
    command: `mcx${process.platform === "win32" ? ".bat" : ""}`,
    args: ["lsp"],
  };
  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: "file", language: "mcx" }],
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
