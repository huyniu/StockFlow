// Shared application state and feature contracts; never exposes credentials on window.
export const app = Object.create(null);

// Keep bindings live: features register before their state is initialized.
export function registerFeature(bindings) {
    const descriptors = Object.fromEntries(
        Object.entries(bindings).map(([name, binding]) => [
            name,
            typeof binding === 'function' ? { get: binding } : binding,
        ]),
    );
    Object.defineProperties(app, descriptors);
}
