package com.suplab.aether.memory.domain;

/**
 * Taxonomy of shared memory that Aether Memory manages.
 *
 * <p>Mirrors the four-type cognitive model of Aether Core, generalised from an individual
 * to a team or organisation. A shared memory is knowledge that belongs to a <em>group</em>
 * rather than a single user.</p>
 *
 * <ul>
 *   <li>EPISODIC   — team events and shared experiences ("the Q3 incident retrospective")</li>
 *   <li>SEMANTIC   — organisational facts and domain knowledge ("our SLA is 99.95%")</li>
 *   <li>PROCEDURAL — shared skills, playbooks, and conventions ("how the team deploys")</li>
 *   <li>EMOTIONAL  — collective sentiment and team affect ("morale after the launch")</li>
 * </ul>
 */
public enum MemoryType {
    EPISODIC,
    SEMANTIC,
    PROCEDURAL,
    EMOTIONAL
}
